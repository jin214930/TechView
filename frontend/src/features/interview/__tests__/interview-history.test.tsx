import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { AuthApi } from "@/features/auth/api/auth-api";
import { AuthProviderCore } from "@/features/auth/model/auth-context";
import type { InterviewFeedbackApi } from "@/features/interview/api/interview-feedback-api";
import { InterviewHistory } from "@/features/interview/ui/interview-history";

const { replaceMock } = vi.hoisted(() => ({ replaceMock: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock }),
}));

const member = {
  id: 1,
  email: "user@example.com",
  nickname: "테스트 사용자",
};

function createAuthApi(overrides: Partial<AuthApi> = {}): AuthApi {
  return {
    login: vi.fn(),
    signUp: vi.fn(),
    refresh: vi.fn().mockResolvedValue("access-token"),
    getMe: vi.fn().mockResolvedValue(member),
    logout: vi.fn(),
    ...overrides,
  };
}

function createFeedbackApi(
  overrides: Partial<InterviewFeedbackApi> = {},
): InterviewFeedbackApi {
  return {
    getHistory: vi.fn().mockResolvedValue({
      items: [
        {
          sessionId: 10,
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
    }),
    getFeedback: vi.fn(),
    ...overrides,
  };
}

describe("InterviewHistory", () => {
  afterEach(() => {
    cleanup();
    replaceMock.mockReset();
  });

  it("loads history and links completed feedback", async () => {
    const api = createFeedbackApi();

    render(
      <AuthProviderCore api={createAuthApi()}>
        <InterviewHistory page={0} size={10} api={api} />
      </AuthProviderCore>,
    );

    const completedInterview = await screen.findByRole("link", { name: /2026/ });
    expect(api.getHistory).toHaveBeenCalledWith(0, 10);
    expect(completedInterview.getAttribute("href")).toBe(
      "/interviews/history/10",
    );
  });

  it("redirects anonymous users with the current pagination query", async () => {
    const api = createFeedbackApi();
    const authApi = createAuthApi({
      refresh: vi.fn().mockRejectedValue(new Error("no session")),
    });

    render(
      <AuthProviderCore api={authApi}>
        <InterviewHistory page={2} size={5} api={api} />
      </AuthProviderCore>,
    );

    await waitFor(() =>
      expect(replaceMock).toHaveBeenCalledWith(
        "/login?next=%2Finterviews%2Fhistory%3Fpage%3D2%26size%3D5",
      ),
    );
    expect(api.getHistory).not.toHaveBeenCalled();
  });
});
