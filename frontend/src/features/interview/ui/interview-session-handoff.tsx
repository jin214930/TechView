"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import { useAuth } from "@/features/auth/model/auth-context";
import { createInterviewApi, type InterviewApi } from "@/features/interview/api/interview-api";
import { getInterviewErrorMessage } from "@/features/interview/model/interview-error";
import { clearInterviewStart } from "@/features/interview/model/interview-session-storage";
import type { InterviewProgress } from "@/features/interview/model/interview-types";
import { getApiClient } from "@/shared/api/client";

interface InterviewSessionHandoffProps {
  sessionId: string;
  api?: InterviewApi;
}

export function InterviewSessionHandoff({
  sessionId,
  api,
}: InterviewSessionHandoffProps) {
  const router = useRouter();
  const { status } = useAuth();
  const apiRef = useRef<InterviewApi | null>(api ?? null);
  const loadedForAuthenticatedRef = useRef(false);
  const [progress, setProgress] = useState<InterviewProgress | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const resolveApi = useCallback(() => {
    apiRef.current ??= api ?? createInterviewApi(getApiClient());
    return apiRef.current;
  }, [api]);

  const loadProgress = useCallback(async () => {
    const numericSessionId = Number(sessionId);
    if (!Number.isSafeInteger(numericSessionId) || numericSessionId <= 0) {
      clearInterviewStart();
      setError("유효하지 않은 면접 세션입니다.");
      setIsLoading(false);
      return;
    }

    setError(null);
    setIsLoading(true);

    try {
      const result = await resolveApi().getProgress(numericSessionId);
      setProgress(result);
    } catch (cause) {
      clearInterviewStart();
      setProgress(null);
      setError(getInterviewErrorMessage(cause));
    } finally {
      setIsLoading(false);
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
          <ProgressState progress={progress} />
        ) : null}
      </section>
    </main>
  );
}

function ProgressState({ progress }: { progress: InterviewProgress }) {
  if (progress.status === "TURN_READY" && progress.question) {
    return (
      <>
        <p className="text-sm font-bold uppercase tracking-[0.2em] text-blue-600">
          Question {progress.turnOrder ?? "-"}
        </p>
        <h1 className="mt-4 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">
          질문이 준비되었습니다.
        </h1>
        <p className="mx-auto mt-6 max-w-2xl rounded-2xl bg-slate-50 px-5 py-6 text-lg font-semibold leading-8 text-slate-800">
          {progress.question}
        </p>
        <p className="mt-6 text-sm leading-6 text-slate-500">
          음성 답변 화면은 다음 단계에서 연결됩니다.
        </p>
      </>
    );
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
