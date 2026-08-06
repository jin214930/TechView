import type { ReactNode } from "react";

interface AuthPageShellProps {
  eyebrow: string;
  title: string;
  description: string;
  children: ReactNode;
}

export function AuthPageShell({
  eyebrow,
  title,
  description,
  children,
}: AuthPageShellProps) {
  return (
    <main className="relative flex flex-1 items-center overflow-hidden bg-slate-50 px-5 py-12 sm:px-8 lg:py-16">
      <div
        aria-hidden="true"
        className="absolute -left-24 top-10 h-72 w-72 rounded-full bg-blue-200/45 blur-3xl"
      />
      <div
        aria-hidden="true"
        className="absolute -right-20 bottom-0 h-80 w-80 rounded-full bg-violet-200/45 blur-3xl"
      />

      <div className="relative mx-auto grid w-full max-w-5xl items-center gap-12 lg:grid-cols-[1fr_460px]">
        <section className="hidden lg:block">
          <p className="text-sm font-bold uppercase tracking-[0.2em] text-blue-600">
            {eyebrow}
          </p>
          <h1 className="mt-5 max-w-xl text-5xl font-bold leading-[1.15] tracking-tight text-slate-950">
            실전처럼 답하고,
            <br />더 나은 답변을 만들어 보세요.
          </h1>
          <p className="mt-6 max-w-lg text-lg leading-8 text-slate-600">
            TechView가 질문부터 꼬리 질문, 면접 종료 후 피드백까지
            함께합니다.
          </p>
          <div className="mt-10 flex gap-3" aria-label="TechView 특징">
            {['15분 면접', '핵심 질문 5개', 'AI 종합 피드백'].map((item) => (
              <span
                key={item}
                className="rounded-full border border-blue-100 bg-white/80 px-4 py-2 text-sm font-semibold text-slate-700 shadow-sm"
              >
                {item}
              </span>
            ))}
          </div>
        </section>

        <section className="rounded-3xl border border-white/80 bg-white/95 p-6 shadow-[0_24px_80px_-30px_rgba(30,64,175,0.35)] backdrop-blur sm:p-9">
          <p className="text-sm font-bold text-blue-600 lg:hidden">{eyebrow}</p>
          <h2 className="mt-2 text-3xl font-bold tracking-tight text-slate-950 lg:mt-0">
            {title}
          </h2>
          <p className="mt-3 text-sm leading-6 text-slate-600">{description}</p>
          <div className="mt-8">{children}</div>
        </section>
      </div>
    </main>
  );
}
