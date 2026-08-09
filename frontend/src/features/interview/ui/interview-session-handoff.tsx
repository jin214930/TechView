"use client";

import { useEffect, useState } from "react";

import { INTERVIEW_START_STORAGE_KEY } from "@/features/interview/model/interview-session-storage";
import type { InterviewStart } from "@/features/interview/model/interview-types";

interface InterviewSessionHandoffProps {
  sessionId: string;
}

export function InterviewSessionHandoff({
  sessionId,
}: InterviewSessionHandoffProps) {
  const [start, setStart] = useState<InterviewStart | null>(null);
  const [isLoaded, setIsLoaded] = useState(false);

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => {
      setStart(null);

      try {
        const raw = window.sessionStorage.getItem(INTERVIEW_START_STORAGE_KEY);
        const parsed = raw ? (JSON.parse(raw) as InterviewStart) : null;

        if (parsed && String(parsed.sessionId) === sessionId) {
          setStart(parsed);
        }
      } catch {
        setStart(null);
      } finally {
        setIsLoaded(true);
      }
    });

    return () => window.cancelAnimationFrame(frame);
  }, [sessionId]);

  return (
    <main className="flex flex-1 items-center bg-slate-50 px-5 py-12 sm:px-8 sm:py-16">
      <section className="mx-auto w-full max-w-3xl rounded-3xl border border-slate-200 bg-white p-6 text-center shadow-[0_24px_80px_-30px_rgba(30,64,175,0.28)] sm:p-9">
        {!isLoaded ? (
          <p role="status" className="text-sm font-semibold text-slate-600">
            면접 정보를 준비하고 있습니다.
          </p>
        ) : start ? (
          <>
            <p className="text-sm font-bold uppercase tracking-[0.2em] text-blue-600">
              Question {start.turnOrder}
            </p>
            <h1 className="mt-4 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">
              질문이 준비되었습니다.
            </h1>
            <p className="mx-auto mt-6 max-w-2xl rounded-2xl bg-slate-50 px-5 py-6 text-lg font-semibold leading-8 text-slate-800">
              {start.question}
            </p>
            <p className="mt-6 text-sm leading-6 text-slate-500">
              음성 답변 화면은 다음 단계에서 연결됩니다.
            </p>
          </>
        ) : (
          <div role="alert">
            <h1 className="text-2xl font-bold text-slate-950">
              면접 정보를 찾을 수 없습니다.
            </h1>
            <p className="mt-3 text-sm leading-6 text-slate-600">
              면접 준비 화면에서 다시 시작해 주세요.
            </p>
          </div>
        )}
      </section>
    </main>
  );
}
