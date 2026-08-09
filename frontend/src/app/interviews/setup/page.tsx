import type { Metadata } from "next";

import { InterviewEnvironmentSetup } from "@/features/interview/ui/interview-environment-setup";

export const metadata: Metadata = {
  title: "면접 환경 설정 | TechView",
};

export default function InterviewSetupPage() {
  return <InterviewEnvironmentSetup />;
}
