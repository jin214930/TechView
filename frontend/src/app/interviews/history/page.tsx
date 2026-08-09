import type { Metadata } from "next";

import { InterviewHistory } from "@/features/interview/ui/interview-history";

export const metadata: Metadata = {
  title: "내 면접 기록 | TechView",
};

interface InterviewHistoryPageProps {
  searchParams: Promise<{ page?: string; size?: string }>;
}

export default async function InterviewHistoryPage({
  searchParams,
}: InterviewHistoryPageProps) {
  const params = await searchParams;

  return (
    <InterviewHistory
      page={parseQueryNumber(params.page, 0, 0)}
      size={parseQueryNumber(params.size, 10, 1)}
    />
  );
}

function parseQueryNumber(
  value: string | undefined,
  fallback: number,
  minimum: number,
): number {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < minimum) {
    return fallback;
  }
  return parsed;
}
