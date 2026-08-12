import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { StrictMode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { AuthApi } from "@/features/auth/api/auth-api";
import { AuthProviderCore } from "@/features/auth/model/auth-context";
import type { InterviewApi } from "@/features/interview/api/interview-api";
import type {
  InterviewEvent,
  InterviewEventsApi,
} from "@/features/interview/api/interview-events-api";
import type { InterviewFeedbackApi } from "@/features/interview/api/interview-feedback-api";
import type { InterviewProgress } from "@/features/interview/model/interview-types";
import { InterviewSessionHandoff } from "@/features/interview/ui/interview-session-handoff";
import { ApiClientError } from "@/shared/api/response";

const { releaseRetainedMicrophoneStreamMock, replaceMock } = vi.hoisted(() => ({
  releaseRetainedMicrophoneStreamMock: vi.fn(),
  replaceMock: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock }),
}));

vi.mock(
  "@/features/interview/model/audio-environment",
  async (importOriginal) => ({
    ...(await importOriginal<
      typeof import("@/features/interview/model/audio-environment")
    >()),
    releaseRetainedMicrophoneStream: releaseRetainedMicrophoneStreamMock,
  }),
);

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
    deleteMe: vi.fn().mockResolvedValue(undefined),
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
  feedbackApi?: InterviewFeedbackApi,
) {
  return render(
    <AuthProviderCore api={authApi}>
      <InterviewSessionHandoff
        sessionId="10"
        api={api}
        eventsApi={eventsApi}
        feedbackApi={feedbackApi}
      />
    </AuthProviderCore>,
  );
}

function createFeedbackApi(
  overrides: Partial<InterviewFeedbackApi> = {},
): InterviewFeedbackApi {
  return {
    getHistory: vi.fn(),
    getFeedback: vi.fn().mockResolvedValue({
      sessionId: 10,
      status: "PROCESSING",
      feedback: null,
      errorMessage: null,
    }),
    ...overrides,
  };
}

describe("InterviewSessionHandoff", () => {
  afterEach(() => {
    cleanup();
    window.sessionStorage.clear();
    releaseRetainedMicrophoneStreamMock.mockReset();
    replaceMock.mockReset();
    vi.useRealTimers();
  });

  it("keeps the microphone stream during Strict Mode effect replay", async () => {
    vi.useFakeTimers();
    const api = createInterviewApi();

    const { unmount } = render(
      <StrictMode>
        <AuthProviderCore api={createAuthApi()}>
          <InterviewSessionHandoff sessionId="10" api={api} />
        </AuthProviderCore>
      </StrictMode>,
    );

    await act(async () => {
      await vi.runAllTimersAsync();
    });
    expect(releaseRetainedMicrophoneStreamMock).not.toHaveBeenCalled();

    unmount();
    await act(async () => {
      await vi.runAllTimersAsync();
    });
    expect(releaseRetainedMicrophoneStreamMock).toHaveBeenCalledOnce();
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

  it("shows generated feedback after the feedback status event", async () => {
    let onEvent!: (event: InterviewEvent) => void;
    const eventsApi: InterviewEventsApi = {
      subscribe: vi.fn((_sessionId, handlers) => {
        onEvent = handlers.onEvent;
        return { close: vi.fn() };
      }),
    };
    const api = createInterviewApi({
      getProgress: vi.fn().mockResolvedValue({
        sessionId: 10,
        status: "COMPLETED",
        turnId: null,
        turnOrder: null,
        turnType: null,
        question: null,
        errorCode: null,
        errorMessage: null,
      }),
    });
    const feedbackApi = createFeedbackApi({
      getFeedback: vi
        .fn()
        .mockResolvedValueOnce({
          sessionId: 10,
          status: "PROCESSING",
          feedback: null,
          errorMessage: null,
        })
        .mockResolvedValueOnce({
          sessionId: 10,
          status: "COMPLETED",
          feedback: {
            overallSummary: "완료된 종합 피드백",
            strengths: ["강점"],
            improvements: ["개선점"],
            categoryFeedbacks: [
              {
                category: "자료구조",
                assessment: "평가",
                recommendation: "추천",
              },
            ],
            recommendedTopics: ["그래프"],
          },
          errorMessage: null,
        }),
    });

    renderHandoff(api, createAuthApi(), eventsApi, feedbackApi);

    expect(
      await screen.findByRole("heading", {
        name: "면접 피드백을 생성 중입니다.",
      }),
    ).toBeTruthy();
    expect(releaseRetainedMicrophoneStreamMock).toHaveBeenCalled();
    expect(
      screen.getByRole("link", { name: "면접 종료" }).getAttribute("href"),
    ).toBe("/");
    onEvent({ name: "feedback-status-changed", sessionId: 10 });

    expect(await screen.findByText("완료된 종합 피드백")).toBeTruthy();
    expect(screen.getByRole("link", { name: "면접 종료" })).toBeTruthy();
    expect(feedbackApi.getFeedback).toHaveBeenCalledTimes(2);
  });

  it("refreshes processing feedback when SSE reconnects", async () => {
    let onEvent!: (event: InterviewEvent) => void;
    const eventsApi: InterviewEventsApi = {
      subscribe: vi.fn((_sessionId, handlers) => {
        onEvent = handlers.onEvent;
        return { close: vi.fn() };
      }),
    };
    const api = createInterviewApi({
      getProgress: vi.fn().mockResolvedValue({
        sessionId: 10,
        status: "COMPLETED",
        turnId: null,
        turnOrder: null,
        turnType: null,
        question: null,
        errorCode: null,
        errorMessage: null,
      }),
    });
    const feedbackApi = createFeedbackApi();

    renderHandoff(api, createAuthApi(), eventsApi, feedbackApi);
    await screen.findByText("면접 피드백을 생성 중입니다.");
    await waitFor(() => expect(eventsApi.subscribe).toHaveBeenCalled());

    onEvent({ name: "connected", sessionId: 10 });

    await waitFor(() => expect(feedbackApi.getFeedback).toHaveBeenCalledTimes(2));
  });

  it("shows the feedback failure and a link to interview history", async () => {
    const api = createInterviewApi({
      getProgress: vi.fn().mockResolvedValue({
        sessionId: 10,
        status: "COMPLETED",
        turnId: null,
        turnOrder: null,
        turnType: null,
        question: null,
        errorCode: null,
        errorMessage: null,
      }),
    });
    const feedbackApi = createFeedbackApi({
      getFeedback: vi.fn().mockResolvedValue({
        sessionId: 10,
        status: "FAILED",
        feedback: null,
        errorMessage: "피드백 생성에 실패했습니다.",
      }),
    });

    renderHandoff(api, createAuthApi(), undefined, feedbackApi);

    expect(await screen.findByText("피드백 생성에 실패했습니다.")).toBeTruthy();
    expect(
      screen.getByRole("link", { name: "내 면접으로 이동" }).getAttribute("href"),
    ).toBe("/interviews/history");
    expect(
      screen.getByRole("link", { name: "면접 종료" }).getAttribute("href"),
    ).toBe("/");
  });

  it("releases the microphone when interview progress fails", async () => {
    const api = createInterviewApi({
      getProgress: vi.fn().mockResolvedValue({
        sessionId: 10,
        status: "FAILED",
        turnId: null,
        turnOrder: null,
        turnType: null,
        question: null,
        errorCode: "INTERVIEW_FAILED",
        errorMessage: "면접 처리에 실패했습니다.",
      }),
    });

    renderHandoff(api);

    expect(await screen.findByText("면접 처리에 실패했습니다.")).toBeTruthy();
    expect(releaseRetainedMicrophoneStreamMock).toHaveBeenCalled();
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
