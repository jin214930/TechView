"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import { createInterviewApi, type InterviewApi } from "@/features/interview/api/interview-api";
import { getInterviewErrorMessage } from "@/features/interview/model/interview-error";
import { saveInterviewStart } from "@/features/interview/model/interview-session-storage";
import type { ActiveInterview } from "@/features/interview/model/interview-types";
import { useAuth } from "@/features/auth/model/auth-context";
import { getApiClient } from "@/shared/api/client";

interface InterviewPreparationProps {
  api?: InterviewApi;
}

export function InterviewPreparation({ api }: InterviewPreparationProps = {}) {
  const router = useRouter();
  const { status } = useAuth();
  const apiRef = useRef<InterviewApi | null>(api ?? null);
  const loadedForAuthenticatedRef = useRef(false);
  const [activeInterview, setActiveInterview] = useState<ActiveInterview | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isStarting, setIsStarting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const resolveApi = useCallback(() => {
    apiRef.current ??= api ?? createInterviewApi(getApiClient());
    return apiRef.current;
  }, [api]);

  const loadActiveInterview = useCallback(() => {
    setError(null);
    setIsLoading(true);

    return resolveApi()
      .getActive()
      .then((active) => {
        setActiveInterview(active);
      })
      .catch((cause: unknown) => {
        setError(getInterviewErrorMessage(cause));
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, [resolveApi]);

  useEffect(() => {
    if (status === "loading") {
      return;
    }

    if (status === "anonymous") {
      loadedForAuthenticatedRef.current = false;
      router.replace("/login?next=%2Finterviews");
      return;
    }

    if (loadedForAuthenticatedRef.current) {
      return;
    }

    loadedForAuthenticatedRef.current = true;
    void loadActiveInterview();
  }, [loadActiveInterview, router, status]);

  async function handleStart() {
    setError(null);
    setIsStarting(true);

    try {
      const start = await resolveApi().startOrResume();
      saveInterviewStart(start);
      router.push(`/interviews/${start.sessionId}`);
    } catch (cause) {
      setError(getInterviewErrorMessage(cause));
    } finally {
      setIsStarting(false);
    }
  }

  if (status !== "authenticated") {
    return (
      <main className="flex flex-1 items-center justify-center bg-slate-50 px-5 py-16">
        <p role="status" className="text-sm font-semibold text-slate-600">
          {status === "loading" ? "인증 상태를 확인하고 있습니다." : "로그인 페이지로 이동하고 있습니다."}
        </p>
      </main>
    );
  }

  return (
    <main className="flex flex-1 items-center bg-slate-50 px-5 py-12 sm:px-8 sm:py-16">
      <section className="mx-auto w-full max-w-3xl">
        <div className="text-center">
          <p className="text-sm font-bold uppercase tracking-[0.2em] text-blue-600">
            Interview setup
          </p>
          <h1 className="mt-4 text-4xl font-black tracking-tight text-slate-950 sm:text-5xl">
            면접을 시작해 볼까요?
          </h1>
          <p className="mx-auto mt-5 max-w-xl text-base leading-7 text-slate-600 sm:text-lg">
            약 15분 동안 핵심 질문 5개에 답하며 실전처럼 연습해 보세요.
          </p>
        </div>

        <div className="mt-10 rounded-3xl border border-slate-200 bg-white p-6 shadow-[0_24px_80px_-30px_rgba(30,64,175,0.28)] sm:p-9">
          {isLoading ? <LoadingState /> : null}

          {!isLoading && activeInterview?.hasActiveSession ? (
            <ActiveInterviewState
              activeInterview={activeInterview}
              disabled={isStarting}
              onStart={handleStart}
            />
          ) : null}

          {!isLoading && activeInterview && !activeInterview.hasActiveSession ? (
            <NewInterviewState disabled={isStarting} onStart={handleStart} />
          ) : null}

          {error ? (
            <div className="mt-6 space-y-3" role="alert">
              <p className="rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">
                {error}
              </p>
              <button
                type="button"
                onClick={() => void loadActiveInterview()}
                className="text-sm font-bold text-blue-600 hover:text-blue-700"
              >
                다시 확인하기
              </button>
            </div>
          ) : null}
        </div>
      </section>
    </main>
  );
}

function LoadingState() {
  return (
    <div role="status" aria-label="진행 중인 면접 확인 중" className="space-y-4">
      <div className="h-6 w-40 animate-pulse rounded bg-slate-100" />
      <div className="h-4 w-full animate-pulse rounded bg-slate-100" />
      <div className="h-12 w-full animate-pulse rounded-xl bg-slate-100" />
    </div>
  );
}

function NewInterviewState({
  disabled,
  onStart,
}: {
  disabled: boolean;
  onStart: () => void;
}) {
  return (
    <div className="text-center">
      <span className="inline-flex rounded-full bg-blue-50 px-3 py-1 text-xs font-bold text-blue-700">
        새로운 면접
      </span>
      <h2 className="mt-4 text-2xl font-bold text-slate-950">첫 면접을 시작해 보세요.</h2>
      <p className="mt-3 text-sm leading-6 text-slate-600">
        시작하면 질문이 준비되고, 답변을 제출하면서 다음 질문으로 진행됩니다.
      </p>
      <button
        type="button"
        onClick={onStart}
        disabled={disabled}
        className="mt-7 h-12 w-full rounded-xl bg-gradient-to-r from-blue-600 to-violet-600 text-sm font-bold text-white shadow-lg shadow-blue-200 transition hover:-translate-y-0.5 hover:shadow-xl disabled:cursor-not-allowed disabled:opacity-60"
      >
        {disabled ? "면접을 준비하고 있습니다..." : "면접 시작하기"}
      </button>
    </div>
  );
}

function ActiveInterviewState({
  activeInterview,
  disabled,
  onStart,
}: {
  activeInterview: ActiveInterview;
  disabled: boolean;
  onStart: () => void;
}) {
  return (
    <div>
      <span className="inline-flex rounded-full bg-amber-50 px-3 py-1 text-xs font-bold text-amber-700">
        진행 중인 면접
      </span>
      <h2 className="mt-4 text-2xl font-bold text-slate-950">이어서 면접을 진행할까요?</h2>
      <dl className="mt-6 grid gap-3 rounded-2xl bg-slate-50 p-5 text-sm sm:grid-cols-2">
        <div>
          <dt className="text-slate-500">시작 시각</dt>
          <dd className="mt-1 font-bold text-slate-800">
            {formatStartedAt(activeInterview.startedAt)}
          </dd>
        </div>
        <div>
          <dt className="text-slate-500">현재 진행</dt>
          <dd className="mt-1 font-bold text-slate-800">
            {activeInterview.currentTurnOrder ?? 1}번째 질문
          </dd>
        </div>
      </dl>
      <button
        type="button"
        onClick={onStart}
        disabled={disabled}
        className="mt-7 h-12 w-full rounded-xl bg-gradient-to-r from-blue-600 to-violet-600 text-sm font-bold text-white shadow-lg shadow-blue-200 transition hover:-translate-y-0.5 hover:shadow-xl disabled:cursor-not-allowed disabled:opacity-60"
      >
        {disabled ? "면접을 준비하고 있습니다..." : "면접 이어하기"}
      </button>
    </div>
  );
}

function formatStartedAt(startedAt: string | null): string {
  if (!startedAt) {
    return "확인할 수 없음";
  }

  const date = new Date(startedAt);
  if (Number.isNaN(date.getTime())) {
    return "확인할 수 없음";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}
