import type { InterviewFeedbackDetail } from "@/features/interview/api/interview-feedback-api";
import { FeedbackStatusBadge } from "@/features/interview/ui/interview-history";

interface InterviewFeedbackContentProps {
  feedback: InterviewFeedbackDetail;
}

export function InterviewFeedbackContent({
  feedback,
}: InterviewFeedbackContentProps) {
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
