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

  const feedback = result.feedback;
  return (
    <div>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="text-sm font-bold uppercase tracking-[0.2em] text-blue-600">
            Interview feedback
          </p>
          <h1 className="mt-3 text-3xl font-black tracking-tight text-slate-950">
            면접 피드백
          </h1>
        </div>
        <FeedbackStatusBadge status="COMPLETED" />
      </div>

      <section className="mt-8 rounded-2xl bg-blue-50 p-5">
        <h2 className="text-lg font-bold text-slate-950">종합 요약</h2>
        <p className="mt-3 text-sm leading-7 text-slate-700">
          {feedback.overallSummary}
        </p>
      </section>

      <div className="mt-6 grid gap-6 md:grid-cols-2">
        <FeedbackList title="강점" items={feedback.strengths} tone="blue" />
        <FeedbackList title="개선할 점" items={feedback.improvements} tone="violet" />
      </div>

      <section className="mt-8">
        <h2 className="text-lg font-bold text-slate-950">카테고리별 피드백</h2>
        <div className="mt-3 space-y-3">
          {feedback.categoryFeedbacks.map((item) => (
            <article
              key={item.category}
              className="rounded-2xl border border-slate-200 p-5"
            >
              <h3 className="font-bold text-slate-950">{item.category}</h3>
              <p className="mt-3 text-sm leading-6 text-slate-700">
                {item.assessment}
              </p>
              <p className="mt-2 text-sm leading-6 text-slate-500">
                추천: {item.recommendation}
              </p>
            </article>
          ))}
        </div>
      </section>

      <FeedbackList
        title="추천 학습 주제"
        items={feedback.recommendedTopics}
        tone="slate"
      />
    </div>
  );
}

function FeedbackList({
  title,
  items,
  tone,
}: {
  title: string;
  items: string[];
  tone: "blue" | "violet" | "slate";
}) {
  const tones = {
    blue: "bg-blue-50",
    violet: "bg-violet-50",
    slate: "bg-slate-50",
  } as const;

  return (
    <section className={`rounded-2xl p-5 ${tones[tone]}`}>
      <h2 className="text-lg font-bold text-slate-950">{title}</h2>
      <ul className="mt-3 space-y-2 text-sm leading-6 text-slate-700">
        {items.map((item, index) => (
          <li key={`${item}-${index}`} className="flex gap-2">
            <span aria-hidden="true">•</span>
            <span>{item}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
