import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { AuthApi } from "@/features/auth/api/auth-api";
import { AuthProviderCore } from "@/features/auth/model/auth-context";
import type { InterviewApi } from "@/features/interview/api/interview-api";
import { InterviewPreparation } from "@/features/interview/ui/interview-preparation";

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
  nickname: "테스트 사용자",
};

function createAuthApi(): AuthApi {
  return {
    login: vi.fn(),
    signUp: vi.fn(),
    refresh: vi.fn().mockResolvedValue("access-token"),
    getMe: vi.fn().mockResolvedValue(member),
    deleteMe: vi.fn().mockResolvedValue(undefined),
    logout: vi.fn(),
  };
}

function createInterviewApi(
  overrides: Partial<InterviewApi> = {},
): InterviewApi {
  return {
    getActive: vi.fn().mockResolvedValue({
      hasActiveSession: false,
      sessionId: null,
      startedAt: null,
      currentTurnOrder: null,
    }),
    startOrResume: vi.fn(),
    getProgress: vi.fn(),
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
    authApi.refresh = vi.fn().mockRejectedValue(new Error("no session"));

    render(
      <AuthProviderCore api={authApi}>
        <InterviewPreparation api={createInterviewApi()} />
      </AuthProviderCore>,
    );

    await waitFor(() =>
      expect(replaceMock).toHaveBeenCalledWith("/login?next=%2Finterviews"),
    );
  });

  it("shows the environment setup action when there is no active session", async () => {
    const api = createInterviewApi();
    renderPreparation(api);

    expect(
      await screen.findByRole("button", { name: "면접 환경 확인하기" }),
    ).toBeTruthy();
    expect(api.getActive).toHaveBeenCalledOnce();
  });

  it("opens the environment setup before creating a new interview", async () => {
    const api = createInterviewApi();
    renderPreparation(api);

    fireEvent.click(
      await screen.findByRole("button", { name: "면접 환경 확인하기" }),
    );

    expect(pushMock).toHaveBeenCalledWith("/interviews/setup");
    expect(api.startOrResume).not.toHaveBeenCalled();
  });

  it("opens the same environment setup for an active session", async () => {
    const api = createInterviewApi({
      getActive: vi.fn().mockResolvedValue({
        hasActiveSession: true,
        sessionId: 10,
        startedAt: "2026-08-09T02:00:00+09:00",
        currentTurnOrder: 3,
      }),
    });
    renderPreparation(api);

    expect(
      await screen.findByRole("button", { name: "면접 환경 확인하기" }),
    ).toBeTruthy();
    expect(screen.getByText("이어하기 전에도 음성 환경을 다시 확인합니다.")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "면접 환경 확인하기" }));

    expect(pushMock).toHaveBeenCalledWith("/interviews/setup");
  });
});
