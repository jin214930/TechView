import type { Metadata } from "next";

import { getSafeNextPath } from "@/features/auth/model/safe-next-path";
import { AuthPageShell } from "@/features/auth/ui/auth-page-shell";
import { SignUpForm } from "@/features/auth/ui/signup-form";

export const metadata: Metadata = {
  title: "회원가입 | TechView",
};

interface SignUpPageProps {
  searchParams: Promise<{ next?: string | string[] }>;
}

export default async function SignUpPage({ searchParams }: SignUpPageProps) {
  const params = await searchParams;
  const nextPath = getSafeNextPath(params.next);

  return (
    <AuthPageShell
      eyebrow="Start practicing"
      title="면접 연습을 시작해 볼까요?"
      description="간단한 정보만 입력하면 바로 TechView의 모의 기술 면접을 연습할 수 있어요."
    >
      <SignUpForm nextPath={nextPath} />
    </AuthPageShell>
  );
}
