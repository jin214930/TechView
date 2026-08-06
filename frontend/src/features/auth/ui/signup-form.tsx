"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";

import { useAuth } from "@/features/auth/model/auth-context";
import { getAuthErrorMessage } from "@/features/auth/model/auth-error";
import {
  signUpSchema,
  type SignUpFormInput,
} from "@/features/auth/model/auth-schema";
import { AuthField } from "@/features/auth/ui/auth-field";

interface SignUpFormProps {
  nextPath: string;
}

export function SignUpForm({ nextPath }: SignUpFormProps) {
  const router = useRouter();
  const { status, signUp } = useAuth();
  const [serverError, setServerError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<SignUpFormInput>({
    resolver: zodResolver(signUpSchema),
    defaultValues: {
      email: "",
      nickname: "",
      password: "",
      passwordConfirm: "",
    },
  });

  useEffect(() => {
    if (status === "authenticated") {
      router.replace(nextPath);
    }
  }, [status, router, nextPath]);

  const onSubmit = handleSubmit(async (values) => {
    setServerError(null);
    try {
      await signUp({
        email: values.email,
        nickname: values.nickname,
        password: values.password,
      });
      const params = new URLSearchParams({ signup: "success" });
      if (nextPath !== "/") {
        params.set("next", nextPath);
      }
      router.replace(`/login?${params.toString()}`);
    } catch (error) {
      setServerError(getAuthErrorMessage(error));
    }
  });

  const loginHref =
    nextPath === "/"
      ? "/login"
      : `/login?next=${encodeURIComponent(nextPath)}`;
  const disabled = isSubmitting || status === "loading";

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      <AuthField
        id="signup-email"
        label="이메일"
        type="email"
        autoComplete="email"
        placeholder="name@example.com"
        error={errors.email?.message}
        {...register("email")}
      />
      <AuthField
        id="signup-nickname"
        label="닉네임"
        type="text"
        autoComplete="nickname"
        placeholder="2~20자, 한글·영문·숫자·밑줄"
        error={errors.nickname?.message}
        {...register("nickname")}
      />
      <AuthField
        id="signup-password"
        label="비밀번호"
        type="password"
        autoComplete="new-password"
        placeholder="8~64자, 공백 없이 입력"
        error={errors.password?.message}
        {...register("password")}
      />
      <AuthField
        id="signup-password-confirm"
        label="비밀번호 확인"
        type="password"
        autoComplete="new-password"
        placeholder="비밀번호를 한 번 더 입력해 주세요"
        error={errors.passwordConfirm?.message}
        {...register("passwordConfirm")}
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
        {isSubmitting ? "가입 중..." : "회원가입"}
      </button>

      <p className="text-center text-sm text-slate-600">
        이미 계정이 있나요?{" "}
        <Link href={loginHref} className="font-bold text-blue-600 hover:text-blue-700">
          로그인
        </Link>
      </p>
    </form>
  );
}
