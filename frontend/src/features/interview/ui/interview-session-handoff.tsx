"use client";

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
import { getInterviewErrorMessage } from "@/features/interview/model/interview-error";
import { clearInterviewStart } from "@/features/interview/model/interview-session-storage";
import type { InterviewProgress } from "@/features/interview/model/interview-types";
import { getApiClient } from "@/shared/api/client";
import { InterviewAnswerTurn } from "@/features/interview/ui/interview-answer-turn";

interface InterviewSessionHandoffProps {
  sessionId: string;
  api?: InterviewApi;
  voiceApi?: InterviewVoiceApi;
  eventsApi?: InterviewEventsApi;
}

export function InterviewSessionHandoff({
  sessionId,
  api,
  voiceApi,
  eventsApi,
}: InterviewSessionHandoffProps) {
  const router = useRouter();
  const { status } = useAuth();
  const apiRef = useRef<InterviewApi | null>(api ?? null);
  const eventsApiRef = useRef<InterviewEventsApi | null>(eventsApi ?? null);
  const loadedForAuthenticatedRef = useRef(false);
  const progressRef = useRef<InterviewProgress | null>(null);
  const [progress, setProgress] = useState<InterviewProgress | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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
    void loadProgress();
  }, [loadProgress, router, sessionId, status]);

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
        void loadProgress(false);
      },
      onError() {
        if (progressRef.current) {
          void loadProgress(false);
        }
      },
    });

    return () => subscription.close();
  }, [error, isLoading, loadProgress, resolveEventsApi, sessionId, status]);

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
          />
        ) : null}
      </section>
    </main>
  );
}

function ProgressState({
  progress,
  voiceApi,
}: {
  progress: InterviewProgress;
  voiceApi?: InterviewVoiceApi;
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
    return (
      <div role="status">
        <h1 className="text-2xl font-bold text-slate-950">
          이미 완료된 면접입니다.
        </h1>
        <p className="mt-3 text-sm leading-6 text-slate-600">
          내 면접에서 결과를 확인할 수 있습니다.
        </p>
      </div>
    );
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
