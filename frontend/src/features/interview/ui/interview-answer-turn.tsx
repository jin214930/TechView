"use client";

import { useEffect, useRef, useState } from "react";

import {
  createInterviewVoiceApi,
  type InterviewVoiceApi,
} from "@/features/interview/api/interview-voice-api";
import {
  startAudioAnswerRecording,
  type AudioAnswerRecording,
  type AudioAnswerRecordingSession,
} from "@/features/interview/model/audio-answer-recorder";
import { getInterviewErrorMessage } from "@/features/interview/model/interview-error";
import type { InterviewProgress } from "@/features/interview/model/interview-types";
import { getApiClient } from "@/shared/api/client";

interface InterviewAnswerTurnProps {
  progress: InterviewProgress;
  voiceApi?: InterviewVoiceApi;
}

type AnswerState = "idle" | "recording" | "recorded" | "uploading" | "submitted";

export function InterviewAnswerTurn({
  progress,
  voiceApi,
}: InterviewAnswerTurnProps) {
  const recorderRef = useRef<AudioAnswerRecordingSession | null>(null);
  const [answerState, setAnswerState] = useState<AnswerState>("idle");
  const [recording, setRecording] = useState<AudioAnswerRecording | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    return () => {
      recorderRef.current?.cancel();
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
      }
    };
  }, [previewUrl]);

  async function handleStartRecording() {
    setError(null);
    setRecording(null);
    setPreviewUrl(null);
    setAnswerState("recording");

    try {
      recorderRef.current = await startAudioAnswerRecording();
    } catch (cause) {
      recorderRef.current = null;
      setAnswerState("idle");
      setError(getInterviewErrorMessage(cause));
    }
  }

  async function handleStopRecording() {
    const recorder = recorderRef.current;
    if (!recorder) {
      return;
    }

    try {
      const result = await recorder.stop();
      setRecording(result);
      setPreviewUrl(URL.createObjectURL(result.blob));
      setAnswerState("recorded");
    } catch (cause) {
      setAnswerState("idle");
      setError(getInterviewErrorMessage(cause));
    } finally {
      recorderRef.current = null;
    }
  }

  async function handleSubmitAnswer() {
    if (!recording || progress.turnId === null) {
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
          audioContentType: recording.contentType,
          audioDurationMs: recording.durationMs,
        },
      );
      await resolvedVoiceApi.uploadAudio(
        prepared.uploadUrl,
        recording.blob,
        recording.contentType,
      );
      await resolvedVoiceApi.completeAnswerUpload(prepared.answerId);
      setAnswerState("submitted");
    } catch (cause) {
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

      {error ? (
        <p role="alert" className="mt-5 rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">
          {error}
        </p>
      ) : null}

      <div className="mt-7 flex flex-col gap-3 sm:flex-row">
        {answerState === "recording" ? (
          <button
            type="button"
            onClick={() => void handleStopRecording()}
            className="h-12 flex-1 rounded-xl bg-rose-600 text-sm font-bold text-white transition hover:bg-rose-700"
          >
            녹음 끝내기
          </button>
        ) : (
          <button
            type="button"
            onClick={() => void handleStartRecording()}
            disabled={answerState === "uploading"}
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
