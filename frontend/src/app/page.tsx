"use client";

import Link from "next/link";

import { useAuth } from "@/features/auth/model/auth-context";

export default function Home() {
  const { status } = useAuth();
  const isAuthenticated = status === "authenticated";

  const mainButtonText = isAuthenticated ? "면접 보러가기" : "무료로 시작하기";
  const mainButtonHref = isAuthenticated ? "/interviews" : "/signup";

  return (
    <main className="flex flex-1 flex-col items-center bg-slate-50/50 px-5 py-12 sm:px-8 sm:py-16">
      <section className="mx-auto w-full max-w-4xl text-center">
        <p className="text-xs font-bold uppercase tracking-[0.2em] text-blue-600 sm:text-sm">
          AI Mock Interview
        </p>
        <h1 className="mt-4 text-4xl font-black tracking-tight text-slate-950 sm:text-6xl">
          Tech<span className="text-blue-600">View</span>
        </h1>
        <p className="mx-auto mt-4 max-w-2xl text-base leading-7 text-slate-600 sm:mt-6 sm:text-lg sm:leading-8">
          실제 면접처럼 질문에 답하고, AI가 제공하는 꼬리 질문과 종합
          피드백으로 답변을 개선해 보세요.
        </p>

        <div className="mt-8 flex justify-center">
          <Link
            href={mainButtonHref}
            className="rounded-full bg-gradient-to-r from-blue-600 to-violet-600 px-8 py-3.5 text-base font-bold text-white shadow-lg shadow-blue-200/80 transition hover:-translate-y-0.5 hover:shadow-xl hover:shadow-blue-300/80"
          >
            {mainButtonText}
          </Link>
        </div>
        <p className="mt-3 text-sm text-slate-500">
          무료 모의 면접은 하루 1회 이용할 수 있어요.
        </p>
      </section>

      {/* 핵심 기능 섹션 */}
      <section className="mx-auto mt-16 w-full max-w-4xl">
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          {/* Card 1: 음성 기반 면접 (연파랑) */}
          <div className="rounded-2xl border-2 border-slate-200/80 bg-[#eef5ff] p-6 text-left shadow-sm transition hover:border-blue-500 hover:shadow-md">
            <h3 className="text-lg font-bold text-slate-950">음성 기반 면접</h3>
            <p className="mt-2 text-sm text-slate-500">질문을 듣고 말로 답변</p>
          </div>

          {/* Card 2: 조건부 꼬리 질문 (연보라) */}
          <div className="rounded-2xl border-2 border-slate-200/80 bg-[#f5eefc] p-6 text-left shadow-sm transition hover:border-blue-500 hover:shadow-md">
            <h3 className="text-lg font-bold text-slate-950">
              조건부 꼬리 질문
            </h3>
            <p className="mt-2 text-sm text-slate-500">답변에 따라 추가 확인</p>
          </div>

          {/* Card 3: 종합 피드백 (흰색) */}
          <div className="rounded-2xl border-2 border-slate-200/80 bg-white p-6 text-left shadow-sm transition hover:border-blue-500 hover:shadow-md">
            <h3 className="text-lg font-bold text-slate-950">종합 피드백</h3>
            <p className="mt-2 text-sm text-slate-500">
              강점과 개선 행동 정리
            </p>
          </div>
        </div>
      </section>
    </main>
  );
}
