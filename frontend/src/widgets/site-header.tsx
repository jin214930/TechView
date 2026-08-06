"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { useAuth } from "@/features/auth/model/auth-context";

export function SiteHeader() {
  const router = useRouter();
  const { status, member, logout } = useAuth();
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  async function handleLogout() {
    setIsLoggingOut(true);
    try {
      await logout();
    } catch {
      // AuthProvider always clears local authentication state in finally.
    } finally {
      setIsLoggingOut(false);
      router.replace("/");
    }
  }

  return (
    <header className="relative z-20 border-b border-slate-200/80 bg-white/90 backdrop-blur">
      <div className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between px-5 sm:px-8">
        <Link
          href="/"
          className="text-xl font-black tracking-tight text-slate-950"
          aria-label="TechView 홈"
        >
          Tech<span className="text-blue-600">View</span>
        </Link>

        <nav aria-label="주요 메뉴" className="flex items-center gap-2">
          {status === "loading" ? (
            <span
              aria-label="인증 상태 확인 중"
              className="h-9 w-28 animate-pulse rounded-full bg-slate-100"
            />
          ) : status === "authenticated" ? (
            <>
              <span className="hidden text-sm font-semibold text-slate-700 sm:inline">
                {member?.nickname}님
              </span>
              <button
                type="button"
                onClick={handleLogout}
                disabled={isLoggingOut}
                className="rounded-full border border-slate-200 px-4 py-2 text-sm font-bold text-slate-700 transition hover:border-slate-300 hover:bg-slate-50 disabled:opacity-60"
              >
                {isLoggingOut ? "로그아웃 중" : "로그아웃"}
              </button>
            </>
          ) : (
            <>
              <Link
                href="/login"
                className="rounded-full px-4 py-2 text-sm font-bold text-slate-700 transition hover:bg-slate-100"
              >
                로그인
              </Link>
              <Link
                href="/signup"
                className="rounded-full bg-slate-950 px-4 py-2 text-sm font-bold text-white transition hover:bg-blue-700"
              >
                회원가입
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
