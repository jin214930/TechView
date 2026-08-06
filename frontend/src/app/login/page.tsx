import type { Metadata } from "next";

import { getSafeNextPath } from "@/features/auth/model/safe-next-path";
import { AuthPageShell } from "@/features/auth/ui/auth-page-shell";
import { LoginForm } from "@/features/auth/ui/login-form";

export const metadata: Metadata = {
  title: "로그인 | TechView",
};

interface LoginPageProps {
  searchParams: Promise<{
    next?: string | string[];
    signup?: string | string[];
  }>;
}

export default async function LoginPage({ searchParams }: LoginPageProps) {
  const params = await searchParams;
  const nextPath = getSafeNextPath(params.next);
  const signUpSucceeded =
    (Array.isArray(params.signup) ? params.signup[0] : params.signup) ===
    "success";

  return (
    <AuthPageShell
      eyebrow="Welcome back"
      title="다시 만나서 반가워요"
      description="로그인하고 진행 중인 면접을 이어가거나 새로운 면접을 시작해 보세요."
    >
      <LoginForm
        nextPath={nextPath}
        signUpSucceeded={signUpSucceeded}
      />
    </AuthPageShell>
  );
}
