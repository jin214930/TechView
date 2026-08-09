"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import { useAuth } from "@/features/auth/model/auth-context";
import {
  createInterviewApi,
  type InterviewApi,
} from "@/features/interview/api/interview-api";
import { getInterviewErrorMessage } from "@/features/interview/model/interview-error";
import { testMicrophoneEnvironment } from "@/features/interview/model/audio-environment";
import { saveInterviewStart } from "@/features/interview/model/interview-session-storage";
import { getApiClient } from "@/shared/api/client";

interface InterviewEnvironmentSetupProps {
  api?: InterviewApi;
}

type MicrophoneTestState = "idle" | "recording" | "passed";

export function InterviewEnvironmentSetup({
  api,
}: InterviewEnvironmentSetupProps = {}) {
  const router = useRouter();
  const { status } = useAuth();
  const apiRef = useRef<InterviewApi | null>(api ?? null);
  const [microphoneState, setMicrophoneState] =
    useState<MicrophoneTestState>("idle");
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [isStarting, setIsStarting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const resolveApi = useCallback(() => {
    apiRef.current ??= api ?? createInterviewApi(getApiClient());
    return apiRef.current;
  }, [api]);

  useEffect(() => {
    if (status === "anonymous") {
      router.replace("/login?next=%2Finterviews%2Fsetup");
    }
  }, [router, status]);

  useEffect(() => {
    return () => {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
      }
    };
  }, [previewUrl]);

  async function handleMicrophoneTest() {
    setError(null);
    setPreviewUrl(null);
    setMicrophoneState("recording");

    try {
      const result = await testMicrophoneEnvironment();
      setPreviewUrl(URL.createObjectURL(result.blob));
      setMicrophoneState("passed");
    } catch (cause) {
      setMicrophoneState("idle");
      setError(getInterviewErrorMessage(cause));
    }
  }

  async function handleStartInterview() {
    if (microphoneState !== "passed") {
      setError("먼저 마이크 테스트를 완료해 주세요.");
      return;
    }

    setError(null);
    setIsStarting(true);

    try {
      const start = await resolveApi().startOrResume();
      saveInterviewStart(start);
      router.push(`/interviews/${start.sessionId}`);
    } catch (cause) {
      setError(getInterviewErrorMessage(cause));
    } finally {
      setIsStarting(false);
    }
  }

  if (status !== "authenticated") {
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
      <section className="mx-auto w-full max-w-3xl rounded-3xl border border-slate-200 bg-white p-6 shadow-[0_24px_80px_-30px_rgba(30,64,175,0.28)] sm:p-9">
        <div className="text-center">
          <p className="text-sm font-bold uppercase tracking-[0.2em] text-blue-600">
            Audio check
          </p>
          <h1 className="mt-4 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">
            면접 환경을 확인해 주세요.
          </h1>
          <p className="mx-auto mt-4 max-w-xl text-sm leading-7 text-slate-600 sm:text-base">
            마이크로 짧은 음성을 녹음하고 재생해 본 뒤 면접을 시작합니다. 테스트 음성은 저장하거나 전송하지 않습니다.
          </p>
        </div>

        <div className="mt-8 grid gap-3 sm:grid-cols-3">
          <CheckItem label="마이크 권한" />
          <CheckItem label="음성 녹음" />
          <CheckItem label="음성 재생" />
        </div>

        <div className="mt-8 rounded-2xl bg-slate-50 p-5 text-center">
          {microphoneState === "recording" ? (
            <p role="status" className="font-semibold text-blue-700">
              녹음 중입니다. 잠시만 기다려 주세요.
            </p>
          ) : null}

          {microphoneState === "passed" && previewUrl ? (
            <div className="space-y-4">
              <p className="font-semibold text-emerald-700">
                음성 환경 테스트가 완료되었습니다.
              </p>
              <audio
                controls
                preload="metadata"
                src={previewUrl}
                aria-label="음성 환경 테스트 녹음"
                className="mx-auto w-full max-w-md"
              />
            </div>
          ) : null}

          {microphoneState !== "recording" ? (
            <button
              type="button"
              onClick={() => void handleMicrophoneTest()}
              className="h-12 w-full max-w-md rounded-xl border border-blue-200 bg-white px-5 text-sm font-bold text-blue-700 transition hover:border-blue-500 hover:bg-blue-50"
            >
              {microphoneState === "passed"
                ? "다시 테스트하기"
                : "마이크 테스트 시작"}
            </button>
          ) : null}
        </div>

        {error ? (
          <p role="alert" className="mt-5 rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">
            {error}
          </p>
        ) : null}

        <div className="mt-7 flex flex-col gap-3 sm:flex-row-reverse">
          <button
            type="button"
            onClick={() => void handleStartInterview()}
            disabled={microphoneState !== "passed" || isStarting}
            className="h-12 flex-1 rounded-xl bg-gradient-to-r from-blue-600 to-violet-600 text-sm font-bold text-white shadow-lg shadow-blue-200 transition hover:-translate-y-0.5 hover:shadow-xl disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isStarting ? "면접을 준비하고 있습니다..." : "면접 시작하기"}
          </button>
          <button
            type="button"
            onClick={() => router.push("/interviews")}
            disabled={isStarting}
            className="h-12 flex-1 rounded-xl border border-slate-200 bg-white text-sm font-bold text-slate-700 transition hover:border-slate-300 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            돌아가기
          </button>
        </div>
      </section>
    </main>
  );
}

function CheckItem({ label }: { label: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-center text-sm font-semibold text-slate-700">
      {label}
    </div>
  );
}
