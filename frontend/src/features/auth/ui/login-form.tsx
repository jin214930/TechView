"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";

import { useAuth } from "@/features/auth/model/auth-context";
import { getAuthErrorMessage } from "@/features/auth/model/auth-error";
import {
  loginSchema,
  type LoginFormInput,
} from "@/features/auth/model/auth-schema";
import { AuthField } from "@/features/auth/ui/auth-field";

interface LoginFormProps {
  nextPath: string;
  signUpSucceeded: boolean;
}

export function LoginForm({ nextPath, signUpSucceeded }: LoginFormProps) {
  const router = useRouter();
  const { status, login } = useAuth();
  const [serverError, setServerError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormInput>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  useEffect(() => {
    if (status === "authenticated") {
      router.replace(nextPath);
    }
  }, [status, router, nextPath]);

  const onSubmit = handleSubmit(async (input) => {
    setServerError(null);
    try {
      await login(input);
    } catch (error) {
      setServerError(getAuthErrorMessage(error));
    }
  });

  const signUpHref =
    nextPath === "/"
      ? "/signup"
      : `/signup?next=${encodeURIComponent(nextPath)}`;
  const disabled = isSubmitting || status === "loading";

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      {signUpSucceeded ? (
        <p
          role="status"
          className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-800"
        >
          회원가입이 완료되었습니다. 로그인해 주세요.
        </p>
      ) : null}

      <AuthField
        id="login-email"
        label="이메일"
        type="email"
        autoComplete="email"
        placeholder="name@example.com"
        error={errors.email?.message}
        {...register("email")}
      />
      <AuthField
        id="login-password"
        label="비밀번호"
        type="password"
        autoComplete="current-password"
        placeholder="비밀번호를 입력해 주세요"
        error={errors.password?.message}
        {...register("password")}
      />

      {serverError ? (
        <p
          role="alert"
          aria-label={serverError}
          className="rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700"
        >
          {serverError}
        </p>
      ) : null}

      <button
        type="submit"
        disabled={disabled}
        className="h-12 w-full rounded-xl bg-gradient-to-r from-blue-600 to-violet-600 text-sm font-bold text-white shadow-lg shadow-blue-200 transition hover:-translate-y-0.5 hover:shadow-xl disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:translate-y-0"
      >
        {isSubmitting ? "로그인 중..." : "로그인"}
      </button>

      <p className="text-center text-sm text-slate-600">
        아직 계정이 없나요?{" "}
        <Link href={signUpHref} className="font-bold text-blue-600 hover:text-blue-700">
          회원가입
        </Link>
      </p>
    </form>
  );
}
