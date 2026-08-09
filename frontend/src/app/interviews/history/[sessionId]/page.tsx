import type { Metadata } from "next";

import { InterviewFeedbackDetail } from "@/features/interview/ui/interview-feedback-detail";

export const metadata: Metadata = {
  title: "면접 피드백 | TechView",
};

interface InterviewFeedbackPageProps {
  params: Promise<{ sessionId: string }>;
}

export default async function InterviewFeedbackPage({
  params,
}: InterviewFeedbackPageProps) {
  const { sessionId } = await params;
  return <InterviewFeedbackDetail sessionId={sessionId} />;
}
