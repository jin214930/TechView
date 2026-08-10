"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import { useAuth } from "@/features/auth/model/auth-context";
import {
  createInterviewApi,
  type InterviewApi,
} from "@/features/interview/api/interview-api";
import {
  type InterviewVoiceApi,
} from "@/features/interview/api/interview-voice-api";
import {
  createInterviewEventsApi,
  type InterviewEventsApi,
} from "@/features/interview/api/interview-events-api";
import {
  createInterviewFeedbackApi,
  type InterviewFeedback,
  type InterviewFeedbackApi,
} from "@/features/interview/api/interview-feedback-api";
import { getInterviewErrorMessage } from "@/features/interview/model/interview-error";
import { releaseRetainedMicrophoneStream } from "@/features/interview/model/audio-environment";
import { clearInterviewStart } from "@/features/interview/model/interview-session-storage";
import type { InterviewProgress } from "@/features/interview/model/interview-types";
import { getApiClient } from "@/shared/api/client";
import { InterviewAnswerTurn } from "@/features/interview/ui/interview-answer-turn";
import { InterviewFeedbackContent } from "@/features/interview/ui/interview-feedback-content";
import { FeedbackStatusBadge } from "@/features/interview/ui/interview-history";

interface InterviewSessionHandoffProps {
  sessionId: string;
  api?: InterviewApi;
  voiceApi?: InterviewVoiceApi;
  eventsApi?: InterviewEventsApi;
  feedbackApi?: InterviewFeedbackApi;
}

export function InterviewSessionHandoff({
  sessionId,
  api,
  voiceApi,
  eventsApi,
  feedbackApi,
}: InterviewSessionHandoffProps) {
  const router = useRouter();
  const { status } = useAuth();
  const apiRef = useRef<InterviewApi | null>(api ?? null);
  const eventsApiRef = useRef<InterviewEventsApi | null>(eventsApi ?? null);
  const feedbackApiRef = useRef<InterviewFeedbackApi | null>(feedbackApi ?? null);
  const loadedForAuthenticatedRef = useRef(false);
  const mountedRef = useRef(true);
  const progressRef = useRef<InterviewProgress | null>(null);
  const progressRefreshRef = useRef<Promise<void> | null>(null);
  const progressRefreshQueuedRef = useRef(false);
  const feedbackRefreshRef = useRef<Promise<void> | null>(null);
  const feedbackRefreshQueuedRef = useRef(false);
  const feedbackLoadStartedRef = useRef(false);
  const terminalMicrophoneReleasedRef = useRef(false);
  const microphoneReleaseTimerRef = useRef<number | null>(null);
  const refreshProgressHandlerRef = useRef<(showLoading?: boolean) => void>(
    () => undefined,
  );
  const refreshFeedbackHandlerRef = useRef<(showLoading?: boolean) => void>(
    () => undefined,
  );
  const [progress, setProgress] = useState<InterviewProgress | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<InterviewFeedback | null>(null);
  const [isFeedbackLoading, setIsFeedbackLoading] = useState(false);
  const [feedbackError, setFeedbackError] = useState<string | null>(null);

  const resolveApi = useCallback(() => {
    apiRef.current ??= api ?? createInterviewApi(getApiClient());
    return apiRef.current;
  }, [api]);

  const resolveEventsApi = useCallback(() => {
    if (eventsApiRef.current) {
      return eventsApiRef.current;
    }

    const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL;
    if (!baseUrl) {
      return null;
    }

    eventsApiRef.current = createInterviewEventsApi({ baseUrl });
    return eventsApiRef.current;
  }, []);

  const resolveFeedbackApi = useCallback(() => {
    feedbackApiRef.current ??=
      feedbackApi ?? createInterviewFeedbackApi(getApiClient());
    return feedbackApiRef.current;
  }, [feedbackApi]);

  const loadProgress = useCallback(async (showLoading = true) => {
    const numericSessionId = Number(sessionId);
    if (!Number.isSafeInteger(numericSessionId) || numericSessionId <= 0) {
      clearInterviewStart();
      setError("유효하지 않은 면접 세션입니다.");
      setIsLoading(false);
      return;
    }

    if (showLoading) {
      setError(null);
      setIsLoading(true);
    }

    try {
      const result = await resolveApi().getProgress(numericSessionId);
      progressRef.current = result;
      setProgress(result);
    } catch (cause) {
      if (showLoading || !progressRef.current) {
        clearInterviewStart();
        progressRef.current = null;
        setProgress(null);
        setError(getInterviewErrorMessage(cause));
      }
    } finally {
      if (showLoading) {
        setIsLoading(false);
      }
    }
  }, [resolveApi, sessionId]);

  const refreshProgress = useCallback(
    (showLoading = false) => {
      if (progressRefreshRef.current) {
        progressRefreshQueuedRef.current = true;
        return;
      }

      const request = loadProgress(showLoading);
      progressRefreshRef.current = request.finally(() => {
        progressRefreshRef.current = null;
        if (progressRefreshQueuedRef.current) {
          progressRefreshQueuedRef.current = false;
          refreshProgressHandlerRef.current(false);
        }
      });
    },
    [loadProgress],
  );

  const loadFeedback = useCallback(async (showLoading = false) => {
    const numericSessionId = Number(sessionId);
    if (!Number.isSafeInteger(numericSessionId) || numericSessionId <= 0) {
      if (mountedRef.current) {
        setFeedbackError("유효하지 않은 면접 세션입니다.");
        setIsFeedbackLoading(false);
      }
      return;
    }

    if (mountedRef.current) {
      setFeedbackError(null);
      if (showLoading) {
        setIsFeedbackLoading(true);
      }
    }

    try {
      const result = await resolveFeedbackApi().getFeedback(numericSessionId);
      if (mountedRef.current) {
        setFeedback(result);
      }
    } catch (cause) {
      if (mountedRef.current) {
        setFeedbackError(getInterviewErrorMessage(cause));
      }
    } finally {
      if (mountedRef.current) {
        setIsFeedbackLoading(false);
      }
    }
  }, [resolveFeedbackApi, sessionId]);

  const refreshFeedback = useCallback(
    (showLoading = false) => {
      if (feedbackRefreshRef.current) {
        feedbackRefreshQueuedRef.current = true;
        return;
      }

      const request = loadFeedback(showLoading);
      feedbackRefreshRef.current = request.finally(() => {
        feedbackRefreshRef.current = null;
        if (feedbackRefreshQueuedRef.current) {
          feedbackRefreshQueuedRef.current = false;
          refreshFeedbackHandlerRef.current(false);
        }
      });
    },
    [loadFeedback],
  );

  useEffect(() => {
    refreshProgressHandlerRef.current = refreshProgress;
    return () => {
      refreshProgressHandlerRef.current = () => undefined;
    };
  }, [refreshProgress]);

  useEffect(() => {
    refreshFeedbackHandlerRef.current = refreshFeedback;
    return () => {
      refreshFeedbackHandlerRef.current = () => undefined;
    };
  }, [refreshFeedback]);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (microphoneReleaseTimerRef.current !== null) {
      window.clearTimeout(microphoneReleaseTimerRef.current);
      microphoneReleaseTimerRef.current = null;
    }

    return () => {
      microphoneReleaseTimerRef.current = window.setTimeout(() => {
        microphoneReleaseTimerRef.current = null;
        releaseRetainedMicrophoneStream();
      }, 0);
    };
  }, []);

  useEffect(() => {
    if (status === "loading") {
      return;
    }

    if (status === "anonymous") {
      clearInterviewStart();
      router.replace(
        `/login?next=${encodeURIComponent(`/interviews/${sessionId}`)}`,
      );
      return;
    }

    if (loadedForAuthenticatedRef.current) {
      return;
    }

    loadedForAuthenticatedRef.current = true;
    refreshProgress(true);
  }, [refreshProgress, router, sessionId, status]);

  useEffect(() => {
    if (
      status !== "authenticated" ||
      progress?.status !== "COMPLETED" ||
      feedbackLoadStartedRef.current
    ) {
      return;
    }

    feedbackLoadStartedRef.current = true;
    refreshFeedback(true);
  }, [progress?.status, refreshFeedback, status]);

  useEffect(() => {
    if (
      terminalMicrophoneReleasedRef.current ||
      (progress?.status !== "COMPLETED" && progress?.status !== "FAILED")
    ) {
      return;
    }

    terminalMicrophoneReleasedRef.current = true;
    releaseRetainedMicrophoneStream();
  }, [progress?.status]);

  useEffect(() => {
    if (status !== "authenticated" || isLoading || error) {
      return;
    }

    const numericSessionId = Number(sessionId);
    if (!Number.isSafeInteger(numericSessionId) || numericSessionId <= 0) {
      return;
    }

    const resolvedEventsApi = resolveEventsApi();
    if (!resolvedEventsApi) {
      return;
    }

    const subscription = resolvedEventsApi.subscribe(numericSessionId, {
      onEvent(event) {
        if (event.sessionId !== numericSessionId) {
          return;
        }

        if (
          progressRef.current?.status === "COMPLETED" &&
          (event.name === "connected" || event.name === "feedback-status-changed")
        ) {
          refreshFeedbackHandlerRef.current(false);
        }

        if (event.name !== "feedback-status-changed") {
          refreshProgress(false);
        }
      },
      onError() {
        if (progressRef.current) {
          refreshProgress(false);
        }
      },
    });

    return () => subscription.close();
  }, [error, isLoading, refreshProgress, resolveEventsApi, sessionId, status]);

  return (
    <main className="flex flex-1 items-center bg-slate-50 px-5 py-12 sm:px-8 sm:py-16">
      <section className="mx-auto w-full max-w-3xl rounded-3xl border border-slate-200 bg-white p-6 text-center shadow-[0_24px_80px_-30px_rgba(30,64,175,0.28)] sm:p-9">
        {status === "loading" || (status === "authenticated" && isLoading) ? (
          <p role="status" className="text-sm font-semibold text-slate-600">
            면접 진행 정보를 확인하고 있습니다.
          </p>
        ) : null}

        {status === "anonymous" ? (
          <p role="status" className="text-sm font-semibold text-slate-600">
            로그인 페이지로 이동하고 있습니다.
          </p>
        ) : null}

        {status === "authenticated" && !isLoading && error ? (
          <div role="alert">
            <h1 className="text-2xl font-bold text-slate-950">
              면접 정보를 불러올 수 없습니다.
            </h1>
            <p className="mt-3 text-sm leading-6 text-slate-600">{error}</p>
          </div>
        ) : null}

        {status === "authenticated" && !isLoading && !error && progress ? (
          <ProgressState
            key={`${progress.status}-${progress.turnId ?? "none"}`}
            progress={progress}
            voiceApi={voiceApi}
            feedback={feedback}
            isFeedbackLoading={isFeedbackLoading}
            feedbackError={feedbackError}
            onRetryFeedback={() => refreshFeedback(true)}
          />
        ) : null}
      </section>
    </main>
  );
}

function ProgressState({
  progress,
  voiceApi,
  feedback,
  isFeedbackLoading,
  feedbackError,
  onRetryFeedback,
}: {
  progress: InterviewProgress;
  voiceApi?: InterviewVoiceApi;
  feedback: InterviewFeedback | null;
  isFeedbackLoading: boolean;
  feedbackError: string | null;
  onRetryFeedback(): void;
}) {
  if (progress.status === "TURN_READY" && progress.question) {
    return <InterviewAnswerTurn progress={progress} voiceApi={voiceApi} />;
  }

  if (progress.status === "PROCESSING") {
    return (
      <div role="status">
        <h1 className="text-2xl font-bold text-slate-950">
          질문을 준비하고 있습니다.
        </h1>
        <p className="mt-3 text-sm leading-6 text-slate-600">
          잠시 후 면접 진행 화면에서 확인할 수 있습니다.
        </p>
      </div>
    );
  }

  if (progress.status === "COMPLETED") {
    return <CompletedFeedbackState
      feedback={feedback}
      isLoading={isFeedbackLoading}
      error={feedbackError}
      onRetry={onRetryFeedback}
    />;
  }

  return (
    <div role="alert">
      <h1 className="text-2xl font-bold text-slate-950">
        면접을 준비하지 못했습니다.
      </h1>
      <p className="mt-3 text-sm leading-6 text-slate-600">
        {progress.errorMessage ?? "잠시 후 다시 시도해 주세요."}
      </p>
    </div>
  );
}

function CompletedFeedbackState({
  feedback,
  isLoading,
  error,
  onRetry,
}: {
  feedback: InterviewFeedback | null;
  isLoading: boolean;
  error: string | null;
  onRetry(): void;
}) {
  return (
    <div>
      <CompletedFeedbackBody
        feedback={feedback}
        isLoading={isLoading}
        error={error}
        onRetry={onRetry}
      />
      <div className="mt-8 border-t border-slate-200 pt-6 text-center">
        <Link
          href="/"
          className="inline-flex h-11 items-center justify-center rounded-xl bg-slate-950 px-6 text-sm font-bold text-white transition hover:bg-slate-800"
        >
          면접 종료
        </Link>
      </div>
    </div>
  );
}

function CompletedFeedbackBody({
  feedback,
  isLoading,
  error,
  onRetry,
}: {
  feedback: InterviewFeedback | null;
  isLoading: boolean;
  error: string | null;
  onRetry(): void;
}) {
  if (isLoading || (!feedback && !error)) {
    return <FeedbackProcessingState />;
  }

  if (error) {
    return (
      <div role="alert" className="space-y-4 text-center">
        <p className="rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">
          {error}
        </p>
        <button
          type="button"
          onClick={onRetry}
          className="text-sm font-bold text-blue-600 hover:text-blue-700"
        >
          다시 확인하기
        </button>
      </div>
    );
  }

  if (feedback?.status === "PROCESSING") {
    return <FeedbackProcessingState />;
  }

  if (feedback?.status === "FAILED") {
    return (
      <div role="alert" className="text-center">
        <FeedbackStatusBadge status="FAILED" />
        <h1 className="mt-5 text-3xl font-black text-slate-950">
          피드백을 생성하지 못했습니다.
        </h1>
        <p className="mt-3 text-sm leading-6 text-slate-600">
          {feedback.errorMessage ?? "잠시 후 다시 시도해 주세요."}
        </p>
        <Link
          href="/interviews/history"
          className="mt-6 inline-flex h-11 items-center justify-center rounded-xl bg-blue-600 px-5 text-sm font-bold text-white hover:bg-blue-700"
        >
          내 면접으로 이동
        </Link>
      </div>
    );
  }

  if (feedback?.status === "COMPLETED" && feedback.feedback) {
    return <InterviewFeedbackContent feedback={feedback.feedback} />;
  }

  return (
    <div role="alert">
      <h1 className="text-2xl font-bold text-slate-950">
        피드백 내용을 확인할 수 없습니다.
      </h1>
    </div>
  );
}

function FeedbackProcessingState() {
  return (
    <div role="status" className="text-center">
      <span
        aria-hidden="true"
        className="mx-auto block h-10 w-10 animate-spin rounded-full border-4 border-blue-100 border-t-blue-600"
      />
      <h1 className="mt-5 text-3xl font-black text-slate-950">
        면접 피드백을 생성 중입니다.
      </h1>
      <p className="mt-3 text-sm leading-6 text-slate-600">
        생성이 완료되면 이 화면에 바로 표시됩니다.
      </p>
    </div>
  );
}
