import Link from "next/link";

export default function Home() {
  return (
    <main className="flex flex-1 items-center bg-slate-50 px-6 py-20">
      <section className="mx-auto w-full max-w-4xl text-center">
        <p className="text-sm font-bold uppercase tracking-[0.2em] text-blue-600">
          AI Mock Interview
        </p>
        <h1 className="mt-5 text-5xl font-black tracking-tight text-slate-950 sm:text-6xl">
          Tech<span className="text-blue-600">View</span>
        </h1>
        <p className="mx-auto mt-6 max-w-2xl text-lg leading-8 text-slate-600">
          실제 면접처럼 질문에 답하고, AI가 제공하는 꼬리 질문과 종합
          피드백으로 답변을 개선해 보세요.
        </p>
        <div className="mt-9 flex flex-col justify-center gap-3 sm:flex-row">
          <Link
            href="/signup"
            className="rounded-full bg-gradient-to-r from-blue-600 to-violet-600 px-7 py-3.5 text-sm font-bold text-white shadow-lg shadow-blue-200 transition hover:-translate-y-0.5"
          >
            무료로 시작하기
          </Link>
          <Link
            href="/login"
            className="rounded-full border border-slate-300 bg-white px-7 py-3.5 text-sm font-bold text-slate-700 transition hover:border-blue-300 hover:text-blue-700"
          >
            로그인
          </Link>
        </div>
      </section>
    </main>
  );
}
