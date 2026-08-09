import type { Metadata } from "next";

import { InterviewPreparation } from "@/features/interview/ui/interview-preparation";

export const metadata: Metadata = {
  title: "면접 준비 | TechView",
};

export default function InterviewsPage() {
  return <InterviewPreparation />;
}
