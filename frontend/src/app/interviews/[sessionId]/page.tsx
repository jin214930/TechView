import type { Metadata } from "next";

import { InterviewSessionHandoff } from "@/features/interview/ui/interview-session-handoff";

export const metadata: Metadata = {
  title: "면접 진행 | TechView",
};

interface InterviewSessionPageProps {
  params: Promise<{ sessionId: string }>;
}

export default async function InterviewSessionPage({
  params,
}: InterviewSessionPageProps) {
  const { sessionId } = await params;

  return <InterviewSessionHandoff sessionId={sessionId} />;
}
