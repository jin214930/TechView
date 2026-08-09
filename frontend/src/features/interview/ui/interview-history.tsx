"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import { useAuth } from "@/features/auth/model/auth-context";
import {
  createInterviewFeedbackApi,
  type FeedbackStatus,
  type InterviewFeedbackApi,
  type InterviewHistoryItem,
  type InterviewHistoryPage,
} from "@/features/interview/api/interview-feedback-api";
import { getInterviewErrorMessage } from "@/features/interview/model/interview-error";
import { getApiClient } from "@/shared/api/client";

interface InterviewHistoryProps {
  page: number;
  size: number;
  api?: InterviewFeedbackApi;
}

export function InterviewHistory({ page, size, api }: InterviewHistoryProps) {
  const router = useRouter();
  const { status } = useAuth();
  const apiRef = useRef<InterviewFeedbackApi | null>(api ?? null);
  const [history, setHistory] = useState<InterviewHistoryPage | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const resolveApi = useCallback(() => {
    apiRef.current ??= api ?? createInterviewFeedbackApi(getApiClient());
    return apiRef.current;
  }, [api]);

  const loadHistory = useCallback(() => {
    setError(null);
    setIsLoading(true);

    return resolveApi()
      .getHistory(page, size)
      .then(setHistory)
      .catch((cause: unknown) => setError(getInterviewErrorMessage(cause)))
      .finally(() => setIsLoading(false));
  }, [page, resolveApi, size]);

  useEffect(() => {
    let active = true;

    if (status === "loading") {
      return () => {
        active = false;
      };
    }

    if (status === "anonymous") {
      router.replace(
        `/login?next=${encodeURIComponent(`/interviews/history?page=${page}&size=${size}`)}`,
      );
      return () => {
        active = false;
      };
    }

    void Promise.resolve().then(() => {
      if (active) {
        return loadHistory();
      }
    });

    return () => {
      active = false;
    };
  }, [loadHistory, page, router, size, status]);

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
        <div className="text-center">
          <p className="text-sm font-bold uppercase tracking-[0.2em] text-blue-600">
            My interviews
          </p>
          <h1 className="mt-4 text-4xl font-black tracking-tight text-slate-950 sm:text-5xl">
            내 면접 기록
          </h1>
          <p className="mx-auto mt-5 max-w-xl text-base leading-7 text-slate-600">
            지금까지 진행한 면접과 피드백 상태를 확인할 수 있습니다.
          </p>
        </div>

        <div className="mt-10 rounded-3xl border border-slate-200 bg-white p-6 shadow-[0_24px_80px_-30px_rgba(30,64,175,0.28)] sm:p-9">
          {isLoading ? <HistoryLoadingState /> : null}

          {!isLoading && error ? (
            <div role="alert" className="space-y-4 text-center">
              <p className="rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">
                {error}
              </p>
              <button
                type="button"
                onClick={() => void loadHistory()}
                className="text-sm font-bold text-blue-600 hover:text-blue-700"
              >
                다시 확인하기
              </button>
            </div>
          ) : null}

          {!isLoading && !error && history?.items.length === 0 ? (
            <p role="status" className="py-10 text-center text-sm text-slate-600">
              아직 완료한 면접 기록이 없습니다.
            </p>
          ) : null}

          {!isLoading && !error && history?.items.length ? (
            <>
              <div className="space-y-3" aria-label="면접 기록 목록">
                {history.items.map((item) => (
                  <HistoryItemCard key={item.sessionId} item={item} />
                ))}
              </div>
              <HistoryPagination
                page={history.page}
                size={history.size}
                totalPages={history.totalPages}
                hasNext={history.hasNext}
              />
            </>
          ) : null}
        </div>
      </section>
    </main>
  );
}

function HistoryPagination({
  page,
  size,
  totalPages,
  hasNext,
}: Pick<InterviewHistoryPage, "page" | "size" | "totalPages" | "hasNext">) {
  const hasPrevious = page > 0;

  if (!hasPrevious && !hasNext) {
    return null;
  }

  return (
    <nav
      aria-label="Interview history pagination"
      className="mt-6 flex items-center justify-between border-t border-slate-100 pt-5"
    >
      {hasPrevious ? (
        <Link
          href={`/interviews/history?page=${page - 1}&size=${size}`}
          className="rounded-lg px-3 py-2 text-sm font-bold text-blue-600 hover:bg-blue-50"
        >
          Previous
        </Link>
      ) : (
        <span />
      )}
      <span className="text-sm text-slate-500">
        Page {page + 1} of {totalPages}
      </span>
      {hasNext ? (
        <Link
          href={`/interviews/history?page=${page + 1}&size=${size}`}
          className="rounded-lg px-3 py-2 text-sm font-bold text-blue-600 hover:bg-blue-50"
        >
          Next
        </Link>
      ) : (
        <span />
      )}
    </nav>
  );
}

function HistoryItemCard({ item }: { item: InterviewHistoryItem }) {
  const content = (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <p className="text-sm font-bold text-slate-950">
          {formatDate(item.startedAt)}
        </p>
        <p className="mt-1 text-xs text-slate-500">
          {item.completedAt ? `완료 ${formatDate(item.completedAt)}` : "진행 완료"}
        </p>
      </div>
      <FeedbackStatusBadge status={item.feedbackStatus} />
    </div>
  );

  if (item.feedbackStatus !== "COMPLETED") {
    return (
      <div className="rounded-2xl border border-slate-200 bg-slate-50 px-5 py-4">
        {content}
      </div>
    );
  }

  return (
    <Link
      href={`/interviews/history/${item.sessionId}`}
      className="block rounded-2xl border border-slate-200 px-5 py-4 transition hover:border-blue-400 hover:bg-blue-50/50"
    >
      {content}
    </Link>
  );
}

export function FeedbackStatusBadge({ status }: { status: FeedbackStatus }) {
  const styles = {
    PROCESSING: "bg-amber-50 text-amber-700",
    FAILED: "bg-rose-50 text-rose-700",
    COMPLETED: "bg-emerald-50 text-emerald-700",
  } as const;
  const labels = {
    PROCESSING: "피드백 처리 중",
    FAILED: "피드백 생성 실패",
    COMPLETED: "피드백 확인",
  } as const;

  return (
    <span className={`inline-flex rounded-full px-3 py-1 text-xs font-bold ${styles[status]}`}>
      {labels[status]}
    </span>
  );
}

function HistoryLoadingState() {
  return (
    <div role="status" aria-label="면접 기록을 불러오는 중" className="space-y-4">
      <div className="h-16 animate-pulse rounded-2xl bg-slate-100" />
      <div className="h-16 animate-pulse rounded-2xl bg-slate-100" />
    </div>
  );
}

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}
