"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";

import {
  createInterviewVoiceApi,
  type InterviewVoiceApi,
} from "@/features/interview/api/interview-voice-api";
import {
  MAX_AUDIO_DURATION_MS,
  startAudioAnswerRecording,
  type AudioAnswerRecording,
  type AudioAnswerRecordingSession,
} from "@/features/interview/model/audio-answer-recorder";
import { getInterviewErrorMessage } from "@/features/interview/model/interview-error";
import { MicrophoneSetupRequiredError } from "@/features/interview/model/audio-environment";
import type { InterviewProgress } from "@/features/interview/model/interview-types";
import { getApiClient } from "@/shared/api/client";

interface InterviewAnswerTurnProps {
  progress: InterviewProgress;
  voiceApi?: InterviewVoiceApi;
}

type AnswerState =
  | "idle"
  | "starting"
  | "recording"
  | "recorded"
  | "uploading"
  | "submitted";

interface PreparedAnswerState {
  answerId: number;
  recording: AudioAnswerRecording;
}

export function InterviewAnswerTurn({
  progress,
  voiceApi,
}: InterviewAnswerTurnProps) {
  const recorderRef = useRef<AudioAnswerRecordingSession | null>(null);
  const mountedRef = useRef(true);
  const recordingRequestRef = useRef(0);
  const previewUrlRef = useRef<string | null>(null);
  const [answerState, setAnswerState] = useState<AnswerState>("idle");
  const [recording, setRecording] = useState<AudioAnswerRecording | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [preparedAnswer, setPreparedAnswer] = useState<PreparedAnswerState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isStartingRecording, setIsStartingRecording] = useState(false);
  const [isStoppingRecording, setIsStoppingRecording] = useState(false);
  const [recordingStartedAt, setRecordingStartedAt] = useState<number | null>(null);
  const [recordingDurationMs, setRecordingDurationMs] = useState(0);
  const [requiresEnvironmentSetup, setRequiresEnvironmentSetup] = useState(false);

  useEffect(() => {
    mountedRef.current = true;

    return () => {
      mountedRef.current = false;
      recordingRequestRef.current += 1;
      recorderRef.current?.cancel();
      if (previewUrlRef.current) {
        URL.revokeObjectURL(previewUrlRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (answerState !== "recording" || recordingStartedAt === null) {
      return;
    }

    const updateDuration = () => {
      setRecordingDurationMs(
        Math.min(MAX_AUDIO_DURATION_MS, Math.max(0, Date.now() - recordingStartedAt)),
      );
    };

    updateDuration();
    const timer = window.setInterval(updateDuration, 1000);
    return () => window.clearInterval(timer);
  }, [answerState, recordingStartedAt]);

  function replacePreviewUrl(nextUrl: string | null) {
    if (previewUrlRef.current && previewUrlRef.current !== nextUrl) {
      URL.revokeObjectURL(previewUrlRef.current);
    }
    previewUrlRef.current = nextUrl;
    setPreviewUrl(nextUrl);
  }

  function handleRecordingResult(
    recorder: AudioAnswerRecordingSession,
    requestId: number,
  ) {
    void recorder.result
      .then((result) => {
        if (
          !mountedRef.current ||
          recordingRequestRef.current !== requestId ||
          recorderRef.current !== recorder
        ) {
          return;
        }
        setRecording(result);
        replacePreviewUrl(URL.createObjectURL(result.blob));
        setRecordingStartedAt(null);
        setRecordingDurationMs(result.durationMs);
        setIsStartingRecording(false);
        setIsStoppingRecording(false);
        setAnswerState("recorded");
        recorderRef.current = null;
      })
      .catch((cause) => {
        if (
          !mountedRef.current ||
          recordingRequestRef.current !== requestId ||
          recorderRef.current !== recorder
        ) {
          return;
        }
        recorderRef.current = null;
        setRecordingStartedAt(null);
        setRecordingDurationMs(0);
        setIsStartingRecording(false);
        setIsStoppingRecording(false);
        setAnswerState("idle");
        setError(getInterviewErrorMessage(cause));
      });
  }

  async function handleStartRecording() {
    if (preparedAnswer) {
      setError("현재 답변 업로드를 재시도한 후 다시 녹음할 수 있습니다.");
      return;
    }

    const requestId = ++recordingRequestRef.current;
    recorderRef.current?.cancel();
    recorderRef.current = null;
    setError(null);
    setRequiresEnvironmentSetup(false);
    setRecording(null);
    replacePreviewUrl(null);
    setIsStartingRecording(true);
    setIsStoppingRecording(false);
    setRecordingStartedAt(null);
    setRecordingDurationMs(0);
    setAnswerState("starting");

    try {
      const recorder = await startAudioAnswerRecording();
      if (!mountedRef.current || recordingRequestRef.current !== requestId) {
        recorder.cancel();
        return;
      }
      recorderRef.current = recorder;
      setIsStartingRecording(false);
      setRecordingStartedAt(Date.now());
      setAnswerState("recording");
      handleRecordingResult(recorder, requestId);
    } catch (cause) {
      if (!mountedRef.current || recordingRequestRef.current !== requestId) {
        return;
      }
      recorderRef.current = null;
      setIsStartingRecording(false);
      setRecordingStartedAt(null);
      setRecordingDurationMs(0);
      setAnswerState("idle");
      setRequiresEnvironmentSetup(cause instanceof MicrophoneSetupRequiredError);
      setError(getInterviewErrorMessage(cause));
    }
  }

  async function handleStopRecording() {
    const recorder = recorderRef.current;
    if (isStoppingRecording) {
      return;
    }

    if (isStartingRecording) {
      recordingRequestRef.current += 1;
      recorder?.cancel();
      recorderRef.current = null;
      setIsStartingRecording(false);
      setRecordingStartedAt(null);
      setRecordingDurationMs(0);
      setAnswerState("idle");
      setError("녹음 시작을 취소했습니다. 다시 녹음해 주세요.");
      return;
    }

    if (!recorder) {
      return;
    }

    setIsStoppingRecording(true);
    try {
      await recorder.stop();
    } catch {
      // The session result handler updates the UI with the user-facing error.
    }
  }

  async function handleSubmitAnswer() {
    const activeRecording = preparedAnswer?.recording ?? recording;
    if (!activeRecording || progress.turnId === null) {
      setError("먼저 답변을 녹음해 주세요.");
      return;
    }

    setError(null);
    setAnswerState("uploading");

    try {
      const resolvedVoiceApi = voiceApi ?? createInterviewVoiceApi(getApiClient());
      const prepared = await resolvedVoiceApi.prepareAnswer(
        progress.sessionId,
        progress.turnId,
        {
          audioContentType: activeRecording.contentType,
          audioDurationMs: activeRecording.durationMs,
        },
      );
      if (!mountedRef.current) {
        return;
      }
      setPreparedAnswer({
        answerId: prepared.answerId,
        recording: activeRecording,
      });
      await resolvedVoiceApi.uploadAudio(
        prepared.uploadUrl,
        activeRecording.blob,
        activeRecording.contentType,
      );
      await resolvedVoiceApi.completeAnswerUpload(prepared.answerId);
      if (!mountedRef.current) {
        return;
      }
      setAnswerState("submitted");
    } catch (cause) {
      if (!mountedRef.current) {
        return;
      }
      setAnswerState("recorded");
      setError(getInterviewErrorMessage(cause));
    }
  }

  if (progress.turnId === null || !progress.question) {
    return (
      <div role="alert">
        <h1 className="text-2xl font-bold text-slate-950">
          질문 정보를 확인할 수 없습니다.
        </h1>
        <p className="mt-3 text-sm leading-6 text-slate-600">
          면접 준비 화면으로 돌아가 다시 시도해 주세요.
        </p>
      </div>
    );
  }

  if (answerState === "submitted") {
    return (
      <div role="status">
        <p className="text-sm font-bold uppercase tracking-[0.2em] text-blue-600">
          Question {progress.turnOrder ?? "-"}
        </p>
        <h1 className="mt-4 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">
          답변을 제출했습니다.
        </h1>
        <p className="mt-4 text-sm leading-6 text-slate-600">
          답변을 처리하고 있습니다. 다음 질문은 처리 완료 후 준비됩니다.
        </p>
      </div>
    );
  }

  return (
    <>
      <p className="text-sm font-bold uppercase tracking-[0.2em] text-blue-600">
        Question {progress.turnOrder ?? "-"}
      </p>
        <h1 className="mt-4 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">
          질문이 준비되었습니다.
        </h1>
      <p className="mx-auto mt-6 max-w-2xl rounded-2xl bg-slate-50 px-5 py-6 text-lg font-semibold leading-8 text-slate-800">
        {progress.question}
      </p>

      {previewUrl && recording ? (
        <div className="mt-6 space-y-3">
          <audio
            controls
            preload="metadata"
            src={previewUrl}
            aria-label="녹음한 답변"
            className="mx-auto w-full max-w-md"
          />
          <p className="text-sm text-slate-500">
            녹음 시간 {Math.max(1, Math.round(recording.durationMs / 1000))}초
          </p>
        </div>
      ) : null}

      {answerState === "starting" ? (
        <div className="mt-6 text-sm text-slate-600" aria-live="polite">
          녹음 장치를 준비하고 있습니다.
        </div>
      ) : null}

      {answerState === "recording" ? (
        <div className="mt-6 space-y-3 text-slate-600" aria-live="polite">
          <p className="text-sm">
            녹음 시간{" "}
            <span className="font-mono tabular-nums text-slate-900">
              {formatRecordingDuration(recordingDurationMs)}
            </span>
          </p>
        </div>
      ) : null}

      {error ? (
        <div className="mt-5 space-y-3">
          <p role="alert" className="rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">
            {error}
          </p>
          {requiresEnvironmentSetup ? (
            <Link
              href="/interviews/setup"
              className="inline-flex h-10 items-center justify-center rounded-xl border border-blue-200 px-4 text-sm font-bold text-blue-700 transition hover:border-blue-500 hover:bg-blue-50"
            >
              음성 환경 다시 확인하기
            </Link>
          ) : null}
        </div>
      ) : null}

      <div className="mt-7 flex flex-col gap-3 sm:flex-row">
        {answerState === "starting" || answerState === "recording" ? (
          <button
            type="button"
            onClick={() => void handleStopRecording()}
            disabled={isStoppingRecording}
            className="h-12 flex-1 rounded-xl bg-rose-600 text-sm font-bold text-white transition hover:bg-rose-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isStartingRecording
              ? "녹음 시작 취소"
              : isStoppingRecording
                ? "녹음 저장 중..."
                : "녹음 끝내기"}
          </button>
        ) : (
          <button
            type="button"
            onClick={() => void handleStartRecording()}
            disabled={answerState === "uploading" || preparedAnswer !== null}
            className="h-12 flex-1 rounded-xl border border-blue-200 bg-white text-sm font-bold text-blue-700 transition hover:border-blue-500 hover:bg-blue-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {recording ? "다시 녹음하기" : "답변 녹음하기"}
          </button>
        )}
        <button
          type="button"
          onClick={() => void handleSubmitAnswer()}
          disabled={!recording || answerState === "uploading"}
          className="h-12 flex-1 rounded-xl bg-gradient-to-r from-blue-600 to-violet-600 text-sm font-bold text-white shadow-lg shadow-blue-200 transition hover:-translate-y-0.5 hover:shadow-xl disabled:cursor-not-allowed disabled:opacity-50"
        >
          {answerState === "uploading" ? "답변을 제출하고 있습니다..." : "답변 제출하기"}
        </button>
      </div>
    </>
  );
}

function formatRecordingDuration(durationMs: number) {
  const totalSeconds = Math.floor(durationMs / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;

  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}
