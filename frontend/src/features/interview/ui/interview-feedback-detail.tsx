"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import { useAuth } from "@/features/auth/model/auth-context";
import {
  createInterviewFeedbackApi,
  type InterviewFeedback,
  type InterviewFeedbackApi,
} from "@/features/interview/api/interview-feedback-api";
import { getInterviewErrorMessage } from "@/features/interview/model/interview-error";
import { getApiClient } from "@/shared/api/client";
import { InterviewFeedbackContent } from "@/features/interview/ui/interview-feedback-content";
import { FeedbackStatusBadge } from "@/features/interview/ui/interview-history";

interface InterviewFeedbackDetailProps {
  sessionId: string;
  api?: InterviewFeedbackApi;
}

export function InterviewFeedbackDetail({
  sessionId,
  api,
}: InterviewFeedbackDetailProps) {
  const router = useRouter();
  const { status } = useAuth();
  const apiRef = useRef<InterviewFeedbackApi | null>(api ?? null);
  const [result, setResult] = useState<InterviewFeedback | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const resolveApi = useCallback(() => {
    apiRef.current ??= api ?? createInterviewFeedbackApi(getApiClient());
    return apiRef.current;
  }, [api]);

  const loadFeedback = useCallback(() => {
    const numericSessionId = Number(sessionId);
    if (!Number.isSafeInteger(numericSessionId) || numericSessionId <= 0) {
      setError("유효하지 않은 면접 세션입니다.");
      setIsLoading(false);
      return;
    }

    setError(null);
    setIsLoading(true);
    return resolveApi()
      .getFeedback(numericSessionId)
      .then(setResult)
      .catch((cause: unknown) => setError(getInterviewErrorMessage(cause)))
      .finally(() => setIsLoading(false));
  }, [resolveApi, sessionId]);

  useEffect(() => {
    let active = true;

    if (status === "loading") {
      return () => {
        active = false;
      };
    }

    if (status === "anonymous") {
      router.replace(
        `/login?next=${encodeURIComponent(`/interviews/history/${sessionId}`)}`,
      );
      return () => {
        active = false;
      };
    }

    void Promise.resolve().then(() => {
      if (active) {
        return loadFeedback();
      }
    });

    return () => {
      active = false;
    };
  }, [loadFeedback, router, sessionId, status]);

  if (status !== "authenticated") {
    return (
      <main className="flex flex-1 items-center justify-center bg-slate-50 px-5 py-16">
        <p role="status" className="text-sm font-semibold text-slate-600">
          {status === "loading"
            ? "인증 상태를 확인하고 있습니다."
            : "로그인 페이지로 이동하고 있습니다."}
        </p>
      </main>
    );
  }

  return (
    <main className="flex flex-1 items-center bg-slate-50 px-5 py-12 sm:px-8 sm:py-16">
      <section className="mx-auto w-full max-w-4xl">
        <Link
          href="/interviews/history"
          className="text-sm font-bold text-blue-600 hover:text-blue-700"
        >
          ← 내 면접 기록으로 돌아가기
        </Link>

        <div className="mt-6 rounded-3xl border border-slate-200 bg-white p-6 shadow-[0_24px_80px_-30px_rgba(30,64,175,0.28)] sm:p-9">
          {isLoading ? (
            <p role="status" className="text-center text-sm text-slate-600">
              피드백을 불러오는 중입니다.
            </p>
          ) : null}

          {!isLoading && error ? (
            <div role="alert" className="space-y-4 text-center">
              <p className="rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">
                {error}
              </p>
              <button
                type="button"
                onClick={() => void loadFeedback()}
                className="text-sm font-bold text-blue-600 hover:text-blue-700"
              >
                다시 확인하기
              </button>
            </div>
          ) : null}

          {!isLoading && !error && result ? (
            <FeedbackState result={result} />
          ) : null}
        </div>
      </section>
    </main>
  );
}

function FeedbackState({ result }: { result: InterviewFeedback }) {
  if (result.status === "PROCESSING") {
    return (
      <div role="status" className="text-center">
        <FeedbackStatusBadge status="PROCESSING" />
        <h1 className="mt-5 text-3xl font-black text-slate-950">
          피드백을 생성하고 있습니다.
        </h1>
        <p className="mt-3 text-sm leading-6 text-slate-600">
          잠시 후 다시 확인해 주세요.
        </p>
      </div>
    );
  }

  if (result.status === "FAILED") {
    return (
      <div role="alert" className="text-center">
        <FeedbackStatusBadge status="FAILED" />
        <h1 className="mt-5 text-3xl font-black text-slate-950">
          피드백을 생성하지 못했습니다.
        </h1>
        <p className="mt-3 text-sm leading-6 text-slate-600">
          {result.errorMessage ?? "잠시 후 다시 시도해 주세요."}
        </p>
      </div>
    );
  }

  if (!result.feedback) {
    return (
      <div role="alert" className="text-center">
        <h1 className="text-2xl font-bold text-slate-950">
          피드백 내용을 확인할 수 없습니다.
        </h1>
      </div>
    );
  }

  return <InterviewFeedbackContent feedback={result.feedback} />;
}
