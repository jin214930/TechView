import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { AuthApi } from "@/features/auth/api/auth-api";
import { AuthProviderCore } from "@/features/auth/model/auth-context";
import type { InterviewApi } from "@/features/interview/api/interview-api";
import type {
  InterviewEvent,
  InterviewEventsApi,
} from "@/features/interview/api/interview-events-api";
import type { InterviewProgress } from "@/features/interview/model/interview-types";
import { InterviewSessionHandoff } from "@/features/interview/ui/interview-session-handoff";
import { ApiClientError } from "@/shared/api/response";

const { replaceMock } = vi.hoisted(() => ({
  replaceMock: vi.fn(),
}));

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

function createInterviewApi(
  overrides: Partial<InterviewApi> = {},
): InterviewApi {
  return {
    getActive: vi.fn(),
    startOrResume: vi.fn(),
    getProgress: vi.fn().mockResolvedValue({
      sessionId: 10,
      status: "TURN_READY",
      turnId: 21,
      turnOrder: 1,
      turnType: "CORE",
      question: "서버에서 확인한 질문",
      errorCode: null,
      errorMessage: null,
    }),
    ...overrides,
  };
}

function renderHandoff(
  api: InterviewApi,
  authApi: AuthApi = createAuthApi(),
  eventsApi?: InterviewEventsApi,
) {
  return render(
    <AuthProviderCore api={authApi}>
      <InterviewSessionHandoff
        sessionId="10"
        api={api}
        eventsApi={eventsApi}
      />
    </AuthProviderCore>,
  );
}

describe("InterviewSessionHandoff", () => {
  afterEach(() => {
    cleanup();
    window.sessionStorage.clear();
    replaceMock.mockReset();
  });

  it("renders the question returned after server-side ownership validation", async () => {
    window.sessionStorage.setItem(
      "techview.interview.start",
      JSON.stringify({
        sessionId: 10,
        question: "다른 사용자의 캐시된 질문",
      }),
    );
    const api = createInterviewApi();

    renderHandoff(api);

    expect(
      await screen.findByRole("heading", { name: "질문이 준비되었습니다." }),
    ).toBeTruthy();
    expect(screen.getByText("서버에서 확인한 질문")).toBeTruthy();
    expect(screen.queryByText("다른 사용자의 캐시된 질문")).toBeNull();
    expect(api.getProgress).toHaveBeenCalledWith(10);
  });

  it("redirects anonymous users and clears the cached handoff", async () => {
    window.sessionStorage.setItem("techview.interview.start", "stale-handoff");
    const authApi = createAuthApi({
      refresh: vi.fn().mockRejectedValue(new Error("no session")),
    });

    renderHandoff(createInterviewApi(), authApi);

    await waitFor(() =>
      expect(replaceMock).toHaveBeenCalledWith(
        "/login?next=%2Finterviews%2F10",
      ),
    );
    expect(window.sessionStorage.getItem("techview.interview.start")).toBeNull();
  });

  it("does not render a cached question when the server rejects ownership", async () => {
    window.sessionStorage.setItem(
      "techview.interview.start",
      JSON.stringify({ sessionId: 10, question: "다른 사용자의 캐시된 질문" }),
    );
    const api = createInterviewApi({
      getProgress: vi
        .fn()
        .mockRejectedValue(new ApiClientError(1003, 403, "접근 권한이 없습니다.")),
    });

    renderHandoff(api);

    expect(await screen.findByRole("alert")).toBeTruthy();
    expect(screen.queryByText("다른 사용자의 캐시된 질문")).toBeNull();
    expect(window.sessionStorage.getItem("techview.interview.start")).toBeNull();
  });

  it("refreshes the progress after an SSE event", async () => {
    let onEvent!: (event: InterviewEvent) => void;
    const close = vi.fn();
    const eventsApi: InterviewEventsApi = {
      subscribe: vi.fn((_sessionId, handlers) => {
        onEvent = handlers.onEvent;
        return { close };
      }),
    };
    const api = createInterviewApi({
      getProgress: vi
        .fn()
        .mockResolvedValueOnce({
          sessionId: 10,
          status: "TURN_READY",
          turnId: 21,
          turnOrder: 1,
          turnType: "CORE",
          question: "initial question",
          errorCode: null,
          errorMessage: null,
        })
        .mockResolvedValueOnce({
          sessionId: 10,
          status: "PROCESSING",
          turnId: null,
          turnOrder: null,
          turnType: null,
          question: null,
          errorCode: null,
          errorMessage: null,
        }),
    });

    renderHandoff(api, createAuthApi(), eventsApi);

    await waitFor(() =>
      expect(eventsApi.subscribe).toHaveBeenCalledWith(10, expect.any(Object)),
    );
    onEvent({ name: "progress-changed", sessionId: 10 });

    await waitFor(() => expect(api.getProgress).toHaveBeenCalledTimes(2));
    expect(screen.getByRole("status")).toBeTruthy();

    cleanup();
    expect(close).toHaveBeenCalledOnce();
  });

  it("serializes overlapping progress refreshes and replays the latest event", async () => {
    let onEvent!: (event: InterviewEvent) => void;
    let resolveRefresh!: (progress: InterviewProgress) => void;
    const refreshPromise = new Promise<InterviewProgress>((resolve) => {
      resolveRefresh = resolve;
    });
    const eventsApi: InterviewEventsApi = {
      subscribe: vi.fn((_sessionId, handlers) => {
        onEvent = handlers.onEvent;
        return { close: vi.fn() };
      }),
    };
    const api = createInterviewApi({
      getProgress: vi
        .fn()
        .mockResolvedValueOnce({
          sessionId: 10,
          status: "TURN_READY",
          turnId: 21,
          turnOrder: 1,
          turnType: "CORE",
          question: "initial question",
          errorCode: null,
          errorMessage: null,
        })
        .mockReturnValueOnce(refreshPromise)
        .mockResolvedValueOnce({
          sessionId: 10,
          status: "TURN_READY",
          turnId: 22,
          turnOrder: 2,
          turnType: "CORE",
          question: "next question",
          errorCode: null,
          errorMessage: null,
        }),
    });

    renderHandoff(api, createAuthApi(), eventsApi);
    await waitFor(() =>
      expect(eventsApi.subscribe).toHaveBeenCalledWith(10, expect.any(Object)),
    );

    onEvent({ name: "progress-changed", sessionId: 10 });
    onEvent({ name: "progress-changed", sessionId: 10 });
    await waitFor(() => expect(api.getProgress).toHaveBeenCalledTimes(2));

    resolveRefresh({
      sessionId: 10,
      status: "PROCESSING",
      turnId: null,
      turnOrder: null,
      turnType: null,
      question: null,
      errorCode: null,
      errorMessage: null,
    });

    await waitFor(() => expect(api.getProgress).toHaveBeenCalledTimes(3));
  });
});
