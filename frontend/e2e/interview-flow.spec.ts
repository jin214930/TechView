import { expect, test, type Page, type Route } from "@playwright/test";

const member = {
  id: 1,
  email: "e2e@example.com",
  nickname: "E2E_user",
};

const accessToken = "e2e-access-token";

test.describe("TechView browser flow", () => {
  test("redirects anonymous users from interview history to login", async ({
    page,
  }) => {
    await mockAnonymousSession(page);

    await page.goto("/interviews/history");

    await expect(page).toHaveURL(/\/login\?next=%2Finterviews%2Fhistory/);
    await expect(page.locator("#login-email")).toBeVisible();
  });

  test("signs up, logs in, and opens completed feedback from history", async ({
    page,
  }) => {
    await mockAuthRoutes(page);
    await mockHistoryRoutes(page);

    await page.goto("/signup");
    await page.locator("#signup-email").fill(member.email);
    await page.locator("#signup-nickname").fill(member.nickname);
    await page.locator("#signup-password").fill("password123!");
    await page.locator("#signup-password-confirm").fill("password123!");
    await page.locator("form button[type=submit]").click();

    await expect(page).toHaveURL(/\/login\?signup=success/);

    await page.locator("#login-email").fill(member.email);
    await page.locator("#login-password").fill("password123!");
    await page.locator("form button[type=submit]").click();

    await expect(page).toHaveURL("/");
    await page.goto("/interviews/history");

    const feedbackLink = page.locator('a[href="/interviews/history/42"]');
    await expect(feedbackLink).toBeVisible();
    await feedbackLink.click();

    await expect(page).toHaveURL("/interviews/history/42");
    await expect(
      page.getByRole("heading", { name: "종합 요약", level: 2 }),
    ).toBeVisible();
    await expect(page.getByText("Java")).toBeVisible();
  });

  test("shows a microphone permission error before interview setup can pass", async ({
    page,
  }) => {
    await mockAuthenticatedSession(page);
    await page.addInitScript(() => {
      Object.defineProperty(navigator.mediaDevices, "getUserMedia", {
        configurable: true,
        value: () =>
          Promise.reject(new DOMException("Permission denied", "NotAllowedError")),
      });
    });

    await page.goto("/interviews/setup");
    await page.locator("main button").first().click();

    await expect(page.locator('main [role="alert"]')).toBeVisible();
    await expect(page.locator("main button").nth(1)).toBeDisabled();
  });

  test("shows the first question and blocks submission before recording", async ({
    page,
  }) => {
    await mockAuthenticatedSession(page);
    await mockInterviewAnswerRoutes(page);

    await page.goto("/interviews/42");
    await expect(page.getByText("Explain database indexing")).toBeVisible();
    await expect(page.getByRole("button", { name: "답변 제출하기" })).toBeDisabled();
  });
});

async function mockAnonymousSession(page: Page) {
  await page.route("**/api/v1/auth/refresh", (route) =>
    json(route, 401, { code: 1002, message: "인증되지 않은 사용자입니다." }),
  );
}

async function mockAuthRoutes(page: Page) {
  let authenticated = false;

  await page.route("**/api/v1/auth/refresh", (route) => {
    if (!authenticated) {
      return json(route, 401, { code: 1002, message: "인증되지 않은 사용자입니다." });
    }
    return json(route, 200, { code: 0, message: "성공", data: { accessToken } });
  });
  await page.route("**/api/v1/auth/signup", (route) =>
    json(route, 201, { code: 0, message: "회원가입이 완료되었습니다.", data: null }),
  );
  await page.route("**/api/v1/auth/login", (route) => {
    authenticated = true;
    return json(route, 200, {
      code: 0,
      message: "성공",
      data: { accessToken, memberId: member.id, email: member.email, nickname: member.nickname },
    });
  });
  await page.route("**/api/v1/members/me", (route) =>
    json(route, 200, { code: 0, message: "성공", data: member }),
  );
}

async function mockAuthenticatedSession(page: Page) {
  await page.route("**/api/v1/auth/refresh", (route) =>
    json(route, 200, { code: 0, message: "성공", data: { accessToken } }),
  );
  await page.route("**/api/v1/members/me", (route) =>
    json(route, 200, { code: 0, message: "성공", data: member }),
  );
}

async function mockHistoryRoutes(page: Page) {
  await page.route("**/api/v1/interviews?*", (route) =>
    json(route, 200, {
      code: 0,
      message: "성공",
      data: {
        items: [
          {
            sessionId: 42,
            startedAt: "2026-08-09T10:00:00+09:00",
            completedAt: "2026-08-09T10:15:00+09:00",
            feedbackStatus: "COMPLETED",
          },
        ],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      },
    }),
  );
  await page.route("**/api/v1/interviews/42/feedback", (route) =>
    json(route, 200, {
      code: 0,
      message: "성공",
      data: {
        sessionId: 42,
        status: "COMPLETED",
        feedback: {
          overallSummary: "핵심 개념을 명확하게 설명했습니다.",
          strengths: ["개념 설명이 명확합니다."],
          improvements: ["트레이드오프를 더 설명해 보세요."],
          categoryFeedbacks: [
            { category: "Java", assessment: "좋습니다.", recommendation: "동시성을 복습하세요." },
          ],
          recommendedTopics: ["인덱스 설계"],
        },
        errorMessage: null,
      },
    }),
  );
}

async function mockInterviewAnswerRoutes(page: Page) {
  await page.route("**/api/v1/interviews/42/progress", (route) =>
    json(route, 200, {
      code: 0,
      message: "성공",
      data: {
        sessionId: 42,
        status: "TURN_READY",
        turnId: 7,
        turnOrder: 1,
        turnType: "CORE",
        question: "Explain database indexing",
        errorCode: null,
        errorMessage: null,
      },
    }),
  );
  await page.route("**/api/v1/interviews/42/events", (route) =>
    route.fulfill({
      status: 200,
      contentType: "text/event-stream",
      body: "event: connected\ndata: {\"sessionId\":42}\n\n",
    }),
  );
  await page.route("**/api/v1/interviews/42/turns/7/answers", (route) =>
    json(route, 201, {
      code: 0,
      message: "성공",
      data: {
        answerId: 11,
        created: true,
        uploadUrl: "http://storage.test/audio/11",
        uploadExpiresAt: "2026-08-09T10:20:00Z",
      },
    }),
  );
  await page.route("http://storage.test/audio/11", (route) =>
    route.fulfill({ status: 200, body: "" }),
  );
  await page.route("**/api/v1/answers/11/upload-completion", (route) =>
    json(route, 202, {
      code: 0,
      message: "접수되었습니다.",
      data: { answerId: 11, processingJobId: 21 },
    }),
  );
}

async function json(route: Route, status: number, body: unknown) {
  await route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(body),
  });
}
