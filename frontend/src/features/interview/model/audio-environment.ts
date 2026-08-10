export const MICROPHONE_TEST_DURATION_MS = 60_000;

export interface MicrophoneTestResult {
  blob: Blob;
  durationMs: number;
}

export interface MicrophoneTestSession {
  result: Promise<MicrophoneTestResult>;
  stop(): Promise<MicrophoneTestResult>;
  cancel(): void;
  retainStream(): void;
}

export class AudioEnvironmentError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "AudioEnvironmentError";
  }
}

let retainedMicrophoneStream: MediaStream | null = null;

function stopStream(stream: MediaStream) {
  stream.getTracks().forEach((track) => track.stop());
}

export function getRetainedMicrophoneStream(): MediaStream | null {
  if (!retainedMicrophoneStream) {
    return null;
  }

  if (
    !retainedMicrophoneStream
      .getTracks()
      .some((track) => track.readyState !== "ended")
  ) {
    retainedMicrophoneStream = null;
    return null;
  }

  return retainedMicrophoneStream;
}

export function releaseRetainedMicrophoneStream() {
  if (!retainedMicrophoneStream) {
    return;
  }

  const stream = retainedMicrophoneStream;
  retainedMicrophoneStream = null;
  stopStream(stream);
}

async function requestMicrophoneStream(): Promise<MediaStream> {
  const permission = await navigator.permissions?.query({
    name: "microphone" as PermissionName,
  }).catch(() => null);

  if (permission?.state === "denied") {
    throw new AudioEnvironmentError(
      "브라우저의 마이크 권한이 차단되어 있습니다. 주소창의 사이트 권한에서 마이크를 허용한 후 다시 시도해 주세요.",
    );
  }

  return navigator.mediaDevices.getUserMedia({ audio: true }).catch(() => {
    throw new AudioEnvironmentError(
      "마이크 권한을 허용해야 면접을 진행할 수 있습니다.",
    );
  });
}

export async function startMicrophoneEnvironmentTest(
  maxDurationMs = MICROPHONE_TEST_DURATION_MS,
): Promise<MicrophoneTestSession> {
  if (!navigator.mediaDevices?.getUserMedia) {
    throw new AudioEnvironmentError(
      "이 브라우저에서는 마이크를 사용할 수 없습니다.",
    );
  }

  if (typeof MediaRecorder === "undefined") {
    throw new AudioEnvironmentError(
      "이 브라우저에서는 음성 녹음을 사용할 수 없습니다.",
    );
  }

  const mimeType = getSupportedWebmMimeType();
  if (!mimeType) {
    throw new AudioEnvironmentError(
      "이 브라우저에서는 audio/webm 음성 녹음을 지원하지 않습니다.",
    );
  }

  const stream = await requestMicrophoneStream();

  try {
    const recorder = new MediaRecorder(stream, { mimeType });
    const chunks: BlobPart[] = [];
    const startedAt = performance.now();
    let settled = false;
    let tracksStopped = false;
    let shouldRetainStream = false;
    let stopTimer: number | undefined;
    let resolveResult!: (result: MicrophoneTestResult) => void;
    let rejectResult!: (error: Error) => void;

    const result = new Promise<MicrophoneTestResult>((resolve, reject) => {
      resolveResult = resolve;
      rejectResult = reject;
    });

    const stopTracks = () => {
      if (shouldRetainStream) {
        retainedMicrophoneStream = stream;
        return;
      }
      if (tracksStopped) {
        return;
      }
      tracksStopped = true;
      if (retainedMicrophoneStream === stream) {
        retainedMicrophoneStream = null;
      }
      stopStream(stream);
    };

    const clearStopTimer = () => {
      if (stopTimer !== undefined) {
        window.clearTimeout(stopTimer);
      }
    };

    const settleFailure = (error: Error) => {
      if (settled) {
        return;
      }
      settled = true;
      shouldRetainStream = false;
      clearStopTimer();
      stopTracks();
      rejectResult(error);
    };

    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) {
        chunks.push(event.data);
      }
    };

    recorder.onerror = () => {
      settleFailure(new AudioEnvironmentError("음성 녹음 중 오류가 발생했습니다."));
    };

    recorder.onstop = () => {
      if (settled) {
        return;
      }
      settled = true;
      clearStopTimer();

      const blob = new Blob(chunks, {
        type: recorder.mimeType || mimeType,
      });
      if (blob.size === 0) {
        shouldRetainStream = false;
        stopTracks();
        rejectResult(new AudioEnvironmentError("녹음된 음성을 확인할 수 없습니다."));
        return;
      }

      stopTracks();
      resolveResult({
        blob,
        durationMs: Math.min(
          maxDurationMs,
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
      }, maxDurationMs);
    } catch {
      stopTracks();
      throw new AudioEnvironmentError("음성 녹음을 시작할 수 없습니다.");
    }

    return {
      result,
      retainStream() {
        if (!settled) {
          shouldRetainStream = true;
          retainedMicrophoneStream = stream;
        }
      },
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
        shouldRetainStream = false;
        clearStopTimer();
        stopTracks();
        if (recorder.state === "recording") {
          recorder.stop();
        }
      },
    };
  } catch (cause) {
    stream.getTracks().forEach((track) => track.stop());
    if (cause instanceof AudioEnvironmentError) {
      throw cause;
    }
    throw new AudioEnvironmentError("음성 환경을 확인할 수 없습니다.");
  }
}

export async function testMicrophoneEnvironment(
  maxDurationMs = MICROPHONE_TEST_DURATION_MS,
): Promise<MicrophoneTestResult> {
  const session = await startMicrophoneEnvironmentTest(maxDurationMs);
  return session.result;
}

export function getSupportedWebmMimeType(): string | null {
  const candidates = ["audio/webm;codecs=opus", "audio/webm"];
  return (
    candidates.find((candidate) =>
      MediaRecorder.isTypeSupported?.(candidate),
    ) ?? null
  );
}
