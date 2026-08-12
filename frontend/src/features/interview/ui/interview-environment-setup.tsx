"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import { useAuth } from "@/features/auth/model/auth-context";
import {
  createInterviewApi,
  type InterviewApi,
} from "@/features/interview/api/interview-api";
import { getInterviewErrorMessage } from "@/features/interview/model/interview-error";
import {
  MICROPHONE_TEST_DURATION_MS,
  releaseRetainedMicrophoneStream,
  startMicrophoneEnvironmentTest,
  type MicrophoneTestSession,
} from "@/features/interview/model/audio-environment";
import { saveInterviewStart } from "@/features/interview/model/interview-session-storage";
import { getApiClient } from "@/shared/api/client";

interface InterviewEnvironmentSetupProps {
  api?: InterviewApi;
}

type MicrophoneTestState =
  | "idle"
  | "starting"
  | "recording"
  | "recorded"
  | "passed";

export function InterviewEnvironmentSetup({
  api,
}: InterviewEnvironmentSetupProps = {}) {
  const router = useRouter();
  const { status } = useAuth();
  const apiRef = useRef<InterviewApi | null>(api ?? null);
  const microphoneSessionRef = useRef<MicrophoneTestSession | null>(null);
  const microphoneRequestRef = useRef(0);
  const handingOffToInterviewRef = useRef(false);
  const mountedRef = useRef(true);
  const [microphoneState, setMicrophoneState] =
    useState<MicrophoneTestState>("idle");
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [isPlaybackReady, setIsPlaybackReady] = useState(false);
  const [isStarting, setIsStarting] = useState(false);
  const [isStartingRecording, setIsStartingRecording] = useState(false);
  const [isStoppingRecording, setIsStoppingRecording] = useState(false);
  const [recordingStartedAt, setRecordingStartedAt] = useState<number | null>(
    null,
  );
  const [recordingDurationMs, setRecordingDurationMs] = useState(0);
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
    mountedRef.current = true;

    return () => {
      mountedRef.current = false;
      microphoneRequestRef.current += 1;
      microphoneSessionRef.current?.cancel();
      if (!handingOffToInterviewRef.current) {
        releaseRetainedMicrophoneStream();
      }
    };
  }, []);

  useEffect(() => {
    return () => {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
      }
    };
  }, [previewUrl]);

  useEffect(() => {
    if (microphoneState !== "recording" || recordingStartedAt === null) {
      return;
    }

    const updateDuration = () => {
      setRecordingDurationMs(
        Math.min(
          MICROPHONE_TEST_DURATION_MS,
          Math.max(0, Date.now() - recordingStartedAt),
        ),
      );
    };

    updateDuration();
    const timer = window.setInterval(updateDuration, 1000);
    return () => window.clearInterval(timer);
  }, [microphoneState, recordingStartedAt]);

  async function handleMicrophoneTest() {
    const requestId = ++microphoneRequestRef.current;
    microphoneSessionRef.current?.cancel();
    microphoneSessionRef.current = null;
    releaseRetainedMicrophoneStream();
    setError(null);
    setPreviewUrl(null);
    setIsPlaybackReady(false);
    setIsStartingRecording(true);
    setIsStoppingRecording(false);
    setRecordingStartedAt(null);
    setRecordingDurationMs(0);
    setMicrophoneState("starting");

    try {
      const session = await startMicrophoneEnvironmentTest();
      if (
        !mountedRef.current ||
        microphoneRequestRef.current !== requestId
      ) {
        session.cancel();
        return;
      }

      microphoneSessionRef.current = session;
      session.retainStream();
      setIsStartingRecording(false);
      setRecordingStartedAt(Date.now());
      setMicrophoneState("recording");

      void session.result.then(
        (result) => {
          if (
            !mountedRef.current ||
            microphoneRequestRef.current !== requestId ||
            microphoneSessionRef.current !== session
          ) {
            return;
          }

          microphoneSessionRef.current = null;
          setIsStoppingRecording(false);
          setRecordingStartedAt(null);
          setRecordingDurationMs(result.durationMs);
          setPreviewUrl(URL.createObjectURL(result.blob));
          setMicrophoneState("recorded");
        },
        (cause) => {
          if (
            !mountedRef.current ||
            microphoneRequestRef.current !== requestId ||
            microphoneSessionRef.current !== session
          ) {
            return;
          }

          microphoneSessionRef.current = null;
          setIsStoppingRecording(false);
          setRecordingStartedAt(null);
          setMicrophoneState("idle");
          setError(getInterviewErrorMessage(cause));
        },
      );
    } catch (cause) {
      if (
        !mountedRef.current ||
        microphoneRequestRef.current !== requestId
      ) {
        return;
      }

      setIsStartingRecording(false);
      microphoneSessionRef.current = null;
      setMicrophoneState("idle");
      setError(getInterviewErrorMessage(cause));
    }
  }

  function handleStopMicrophoneTest() {
    const session = microphoneSessionRef.current;
    if (isStartingRecording) {
      microphoneRequestRef.current += 1;
      session?.cancel();
      microphoneSessionRef.current = null;
      setIsStartingRecording(false);
      setRecordingStartedAt(null);
      setRecordingDurationMs(0);
      setMicrophoneState("idle");
      setError("마이크 테스트 시작을 취소했습니다. 다시 시도해 주세요.");
      return;
    }

    if (!session) {
      return;
    }

    session.retainStream();
    setIsStoppingRecording(true);
    void session.stop().catch(() => undefined);
  }

  function handlePlaybackReady() {
    setIsPlaybackReady(true);
  }

  function handlePlaybackError() {
    setIsPlaybackReady(false);
    setMicrophoneState("recorded");
    setError("테스트 음성을 재생할 수 없습니다. 다시 테스트해 주세요.");
  }

  function handleAudibleConfirmation(checked: boolean) {
    if (!checked || !isPlaybackReady) {
      setMicrophoneState("recorded");
      return;
    }
    setError(null);
    setMicrophoneState("passed");
  }

  async function handleStartInterview() {
    if (microphoneState !== "passed") {
      setError("테스트 음성을 재생한 후 소리가 잘 들리는지 확인해 주세요.");
      return;
    }

    setError(null);
    setIsStarting(true);

    try {
      const start = await resolveApi().startOrResume();
      saveInterviewStart(start);
      handingOffToInterviewRef.current = true;
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

  const hasRecording = microphoneState === "recorded" || microphoneState === "passed";

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

        <div className="mt-8 rounded-2xl bg-slate-50 p-5 text-center">
          {microphoneState === "starting" ? (
            <div role="status" className="space-y-4 text-slate-600">
              <p className="font-semibold">녹음 장치를 준비하고 있습니다.</p>
            </div>
          ) : null}

          {microphoneState === "recording" ? (
            <div role="status" className="space-y-4 text-slate-700">
              {isStartingRecording ? (
                <p className="font-semibold text-slate-600">
                  마이크 권한과 녹음 환경을 확인하고 있습니다.
                </p>
              ) : (
                <>
                  <p className="font-semibold text-slate-600">
                    아래 문장을 읽은 뒤 녹음 끝내기를 눌러 주세요.
                  </p>
                  <div className="rounded-2xl border border-slate-200 bg-white p-4 text-left shadow-sm">
                    <p className="text-xs font-bold uppercase tracking-wide text-slate-500">
                      음성 확인 문장
                    </p>
                    <p className="mt-2 text-base font-semibold leading-7 text-slate-900">
                      안녕하세요. 음성 환경 테스트를 진행하고 있습니다.
                    </p>
                  </div>
                  <p className="font-semibold text-slate-600">
                    녹음 시간{" "}
                    <span className="font-mono tabular-nums text-slate-900">
                      {formatRecordingDuration(recordingDurationMs)}
                    </span>
                  </p>
                </>
              )}
            </div>
          ) : null}

          {hasRecording && previewUrl ? (
            <div className="space-y-4">
              {microphoneState === "passed" ? (
                <p className="font-semibold text-emerald-700">
                  음성 환경 테스트가 완료되었습니다.
                </p>
              ) : (
                <p className="font-semibold text-slate-700">
                  녹음된 음성을 재생하고 소리를 확인해 주세요.
                </p>
              )}
              <audio
                controls
                preload="metadata"
                src={previewUrl}
                aria-label="음성 환경 테스트 녹음"
                onCanPlay={handlePlaybackReady}
                onError={handlePlaybackError}
                className="mx-auto w-full max-w-md"
              />
              <p className="text-sm font-semibold text-slate-600">
                녹음 시간 {formatRecordingDuration(recordingDurationMs)}
              </p>
              <label className="flex items-center justify-center gap-2 text-sm font-semibold text-slate-700">
                <input
                  type="checkbox"
                  checked={microphoneState === "passed"}
                  disabled={!isPlaybackReady || isStarting}
                  onChange={(event) =>
                    handleAudibleConfirmation(event.target.checked)
                  }
                />
                소리가 잘 들려요.
              </label>
            </div>
          ) : null}

          {microphoneState === "starting" || microphoneState === "recording" ? (
            <button
              type="button"
              onClick={handleStopMicrophoneTest}
              disabled={isStoppingRecording}
              className="h-12 w-full max-w-md rounded-xl bg-blue-600 px-5 text-sm font-bold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {isStartingRecording
                ? "마이크 테스트 취소"
                : isStoppingRecording
                  ? "녹음 종료 중..."
                  : "녹음 끝내기"}
            </button>
          ) : (
            <button
              type="button"
              onClick={() => void handleMicrophoneTest()}
              disabled={isStartingRecording || isStoppingRecording}
              className="h-12 w-full max-w-md rounded-xl border border-blue-200 bg-white px-5 text-sm font-bold text-blue-700 transition hover:border-blue-500 hover:bg-blue-50"
            >
              {hasRecording ? "다시 테스트하기" : "마이크 테스트 시작"}
            </button>
          )}
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
            disabled={isStarting || isStartingRecording || isStoppingRecording}
            className="h-12 flex-1 rounded-xl border border-slate-200 bg-white text-sm font-bold text-slate-700 transition hover:border-slate-300 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            돌아가기
          </button>
        </div>
      </section>
    </main>
  );
}

function formatRecordingDuration(durationMs: number) {
  const totalSeconds = Math.floor(durationMs / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;

  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}
