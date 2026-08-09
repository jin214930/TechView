import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { InterviewApi } from "@/features/interview/api/interview-api";
import { InterviewPreparation } from "@/features/interview/ui/interview-preparation";
import { AuthProviderCore } from "@/features/auth/model/auth-context";

const { replaceMock, pushMock } = vi.hoisted(() => ({
  replaceMock: vi.fn(),
  pushMock: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock, push: pushMock }),
}));

const member = {
  id: 1,
  email: "user@example.com",
  nickname: "테크뷰",
};

function createAuthApi() {
  return {
    login: vi.fn(),
    signUp: vi.fn(),
    refresh: vi.fn().mockResolvedValue("access-token"),
    getMe: vi.fn().mockResolvedValue(member),
    logout: vi.fn(),
  };
}

function createInterviewApi(overrides: Partial<InterviewApi> = {}): InterviewApi {
  return {
    getActive: vi.fn().mockResolvedValue({
      hasActiveSession: false,
      sessionId: null,
      startedAt: null,
      currentTurnOrder: null,
    }),
    startOrResume: vi.fn().mockResolvedValue({
      resumed: false,
      sessionId: 10,
      turnId: 21,
      turnOrder: 1,
      turnType: "CORE",
      question: "Spring의 장점을 설명해 주세요.",
    }),
    ...overrides,
  };
}

function renderPreparation(api: InterviewApi) {
  return render(
    <AuthProviderCore api={createAuthApi()}>
      <InterviewPreparation api={api} />
    </AuthProviderCore>,
  );
}

describe("InterviewPreparation", () => {
  afterEach(() => {
    cleanup();
    window.sessionStorage.clear();
    replaceMock.mockReset();
    pushMock.mockReset();
  });

  it("redirects anonymous users to login", async () => {
    const authApi = createAuthApi();
    authApi.refresh.mockRejectedValue(new Error("no session"));

    render(
      <AuthProviderCore api={authApi}>
        <InterviewPreparation api={createInterviewApi()} />
      </AuthProviderCore>,
    );

    await waitFor(() =>
      expect(replaceMock).toHaveBeenCalledWith("/login?next=%2Finterviews"),
    );
  });

  it("shows the new interview action when there is no active session", async () => {
    const api = createInterviewApi();
    renderPreparation(api);

    expect(await screen.findByRole("button", { name: "면접 시작하기" })).toBeTruthy();
    expect(api.getActive).toHaveBeenCalledOnce();
  });

  it("stores the start response and navigates to the interview route", async () => {
    const api = createInterviewApi();
    renderPreparation(api);

    fireEvent.click(await screen.findByRole("button", { name: "면접 시작하기" }));

    await waitFor(() => expect(api.startOrResume).toHaveBeenCalledOnce());
    expect(pushMock).toHaveBeenCalledWith("/interviews/10");
    expect(window.sessionStorage.getItem("techview.interview.start")).toContain(
      '"sessionId":10',
    );
  });

  it("shows the current turn when an active session exists", async () => {
    const api = createInterviewApi({
      getActive: vi.fn().mockResolvedValue({
        hasActiveSession: true,
        sessionId: 10,
        startedAt: "2026-08-09T02:00:00+09:00",
        currentTurnOrder: 3,
      }),
    });
    renderPreparation(api);

    expect(await screen.findByRole("button", { name: "면접 이어하기" })).toBeTruthy();
    expect(screen.getByText("3번째 질문")).toBeTruthy();
    expect(screen.getByText("진행 중인 면접")).toBeTruthy();
  });
});
