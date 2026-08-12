"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import { getAuthErrorMessage } from "@/features/auth/model/auth-error";
import { useAuth } from "@/features/auth/model/auth-context";

export function ProfilePage() {
  const router = useRouter();
  const { status, member, deleteAccount } = useAuth();
  const [isConfirmOpen, setIsConfirmOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isLeavingProfile = useRef(false);

  useEffect(() => {
    if (status === "anonymous" && !isLeavingProfile.current) {
      router.replace(`/login?next=${encodeURIComponent("/profile")}`);
    }
  }, [router, status]);

  async function handleDeleteAccount() {
    setError(null);
    setIsDeleting(true);
    isLeavingProfile.current = true;

    try {
      await deleteAccount();
      router.replace("/");
    } catch (cause) {
      isLeavingProfile.current = false;
      setError(getAuthErrorMessage(cause));
    } finally {
      setIsDeleting(false);
    }
  }

  if (status !== "authenticated" || !member) {
    return (
      <main className="flex flex-1 items-center justify-center bg-slate-50 px-5 py-16">
        <p role="status" className="text-sm font-semibold text-slate-600">
          {status === "loading"
            ? "인증 상태를 확인하고 있습니다."
            : "로그인 페이지로 이동하고 있습니다."}
        </p>
      </main>
    );
  }

  return (
    <main className="flex flex-1 items-center bg-slate-50 px-5 py-12 sm:px-8 sm:py-16">
      <section className="mx-auto w-full max-w-2xl">
        <div className="text-center">
          <p className="text-sm font-bold uppercase tracking-[0.2em] text-blue-600">
            My profile
          </p>
          <h1 className="mt-4 text-4xl font-black tracking-tight text-slate-950 sm:text-5xl">
            내 프로필
          </h1>
          <p className="mx-auto mt-5 max-w-xl text-base leading-7 text-slate-600">
            TechView 계정 정보를 확인하고 관리할 수 있습니다.
          </p>
        </div>

        <div className="mt-10 rounded-3xl border border-slate-200 bg-white p-6 shadow-[0_24px_80px_-30px_rgba(30,64,175,0.28)] sm:p-9">
          <dl className="divide-y divide-slate-100">
            <ProfileRow label="닉네임" value={member.nickname} />
            <ProfileRow label="이메일" value={member.email} />
          </dl>

          {error ? (
            <p role="alert" className="mt-6 rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">
              {error}
            </p>
          ) : null}

          <div className="mt-8 flex flex-col gap-3 sm:flex-row sm:justify-end">
            <Link
              href="/interviews/history"
              className="inline-flex h-11 items-center justify-center rounded-xl border border-slate-200 px-5 text-sm font-bold text-slate-700 transition hover:border-blue-300 hover:bg-blue-50"
            >
              내 면접 기록
            </Link>
            <button
              type="button"
              onClick={() => {
                setError(null);
                setIsConfirmOpen(true);
              }}
              className="inline-flex h-11 items-center justify-center rounded-xl border border-rose-200 px-5 text-sm font-bold text-rose-600 transition hover:bg-rose-50"
            >
              회원 탈퇴
            </button>
          </div>
        </div>
      </section>

      {isConfirmOpen ? (
        <div className="fixed inset-0 z-30 flex items-center justify-center bg-slate-950/40 px-5">
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="delete-account-title"
            className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl"
          >
            <h2 id="delete-account-title" className="text-xl font-bold text-slate-950">
              회원 탈퇴를 진행할까요?
            </h2>
            <p className="mt-3 text-sm leading-6 text-slate-600">
              계정과 면접 기록이 삭제되며, 삭제한 데이터는 복구할 수 없습니다.
            </p>
            <div className="mt-6 flex justify-end gap-3">
              <button
                type="button"
                disabled={isDeleting}
                onClick={() => setIsConfirmOpen(false)}
                className="h-10 rounded-lg px-4 text-sm font-bold text-slate-600 hover:bg-slate-100 disabled:opacity-60"
              >
                취소
              </button>
              <button
                type="button"
                disabled={isDeleting}
                onClick={() => void handleDeleteAccount()}
                className="h-10 rounded-lg bg-rose-600 px-4 text-sm font-bold text-white hover:bg-rose-700 disabled:opacity-60"
              >
                {isDeleting ? "탈퇴 처리 중" : "탈퇴하기"}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </main>
  );
}

function ProfileRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-6 py-4 first:pt-0 last:pb-0">
      <dt className="text-sm font-semibold text-slate-500">{label}</dt>
      <dd className="break-all text-right text-sm font-bold text-slate-950">{value}</dd>
    </div>
  );
}
