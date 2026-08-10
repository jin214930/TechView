import {
  AudioEnvironmentError,
  getRetainedMicrophoneStream,
  getSupportedWebmMimeType,
  releaseRetainedMicrophoneStream,
} from "@/features/interview/model/audio-environment";

export const MAX_AUDIO_DURATION_MS = 180_000;

export interface AudioAnswerRecording {
  blob: Blob;
  contentType: "audio/webm";
  durationMs: number;
}

export interface AudioAnswerRecordingSession {
  result: Promise<AudioAnswerRecording>;
  stop(): Promise<AudioAnswerRecording>;
  cancel(): void;
}

export async function startAudioAnswerRecording(): Promise<AudioAnswerRecordingSession> {
  if (!navigator.mediaDevices?.getUserMedia) {
    throw new AudioEnvironmentError("이 브라우저에서는 마이크를 사용할 수 없습니다.");
  }

  if (typeof MediaRecorder === "undefined") {
    throw new AudioEnvironmentError("이 브라우저에서는 음성 녹음을 사용할 수 없습니다.");
  }

  const mimeType = getSupportedWebmMimeType();
  if (!mimeType) {
    throw new AudioEnvironmentError(
      "이 브라우저에서는 audio/webm 음성 녹음을 지원하지 않습니다.",
    );
  }

  const stream = getRetainedMicrophoneStream();
  if (!stream) {
    throw new AudioEnvironmentError(
      "면접 환경 테스트를 먼저 완료한 후 답변을 녹음해 주세요.",
    );
  }

  let tracksStopped = false;
  const stopTracks = () => {
    if (tracksStopped) {
      return;
    }
    tracksStopped = true;
  };

  try {
    const recorder = new MediaRecorder(stream, { mimeType });
    const chunks: BlobPart[] = [];
    const startedAt = performance.now();
    let settled = false;
    let stopTimer: number | undefined;
    let resolveResult!: (recording: AudioAnswerRecording) => void;
    let rejectResult!: (error: Error) => void;

    const result = new Promise<AudioAnswerRecording>((resolve, reject) => {
      resolveResult = resolve;
      rejectResult = reject;
    });

    const clearStopTimer = () => {
      if (stopTimer !== undefined) {
        window.clearTimeout(stopTimer);
      }
    };

    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) {
        chunks.push(event.data);
      }
    };

    recorder.onerror = () => {
      if (settled) {
        return;
      }
      settled = true;
      clearStopTimer();
      stopTracks();
      releaseRetainedMicrophoneStream();
      rejectResult(new AudioEnvironmentError("음성 녹음 중 오류가 발생했습니다."));
    };

    recorder.onstop = () => {
      if (settled) {
        return;
      }
      settled = true;
      clearStopTimer();
      stopTracks();

      const blob = new Blob(chunks, { type: "audio/webm" });
      if (blob.size === 0) {
        releaseRetainedMicrophoneStream();
        rejectResult(new AudioEnvironmentError("녹음된 음성을 확인할 수 없습니다."));
        return;
      }

      resolveResult({
        blob,
        contentType: "audio/webm",
        durationMs: Math.min(
          MAX_AUDIO_DURATION_MS,
          Math.max(1, Math.round(performance.now() - startedAt)),
        ),
      });
    };

    try {
      recorder.start();
      stopTimer = window.setTimeout(() => {
        if (recorder.state === "recording") {
          recorder.stop();
        }
      }, MAX_AUDIO_DURATION_MS);
    } catch {
      stopTracks();
      throw new AudioEnvironmentError("음성 녹음을 시작할 수 없습니다.");
    }

    return {
      result,
      stop() {
        if (!settled && recorder.state === "recording") {
          recorder.stop();
        }
        return result;
      },
      cancel() {
        if (settled) {
          return;
        }
        settled = true;
        clearStopTimer();
        stopTracks();
        if (recorder.state === "recording") {
          recorder.stop();
        }
      },
    };
  } catch (cause) {
    stopTracks();
    releaseRetainedMicrophoneStream();
    if (cause instanceof AudioEnvironmentError) {
      throw cause;
    }
    throw new AudioEnvironmentError("음성 녹음을 시작할 수 없습니다.");
  }
}
