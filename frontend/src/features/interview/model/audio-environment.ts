export const MICROPHONE_TEST_DURATION_MS = 1000;

export interface MicrophoneTestResult {
  blob: Blob;
  durationMs: number;
}

export class AudioEnvironmentError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "AudioEnvironmentError";
  }
}

export async function testMicrophoneEnvironment(
  durationMs = MICROPHONE_TEST_DURATION_MS,
): Promise<MicrophoneTestResult> {
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

  const stream = await navigator.mediaDevices
    .getUserMedia({ audio: true })
    .catch(() => {
      throw new AudioEnvironmentError(
        "마이크 권한을 허용해야 면접을 진행할 수 있습니다.",
      );
    });

  try {
    const recorder = new MediaRecorder(stream, { mimeType });
    const chunks: BlobPart[] = [];
    const startedAt = performance.now();

    return await new Promise<MicrophoneTestResult>((resolve, reject) => {
      let settled = false;
      let stopTimer: number | undefined;

      const stopTracks = () => {
        stream.getTracks().forEach((track) => track.stop());
      };

      const settleFailure = (error: Error) => {
        if (settled) {
          return;
        }
        settled = true;
        if (stopTimer !== undefined) {
          window.clearTimeout(stopTimer);
        }
        stopTracks();
        reject(error);
      };

      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          chunks.push(event.data);
        }
      };

      recorder.onerror = () => {
        settleFailure(
          new AudioEnvironmentError("음성 녹음 중 오류가 발생했습니다."),
        );
      };

      recorder.onstop = () => {
        if (settled) {
          return;
        }
        settled = true;
        if (stopTimer !== undefined) {
          window.clearTimeout(stopTimer);
        }
        stopTracks();

        const blob = new Blob(chunks, {
          type: recorder.mimeType || mimeType,
        });
        if (blob.size === 0) {
          reject(new AudioEnvironmentError("녹음된 음성을 확인할 수 없습니다."));
          return;
        }

        resolve({
          blob,
          durationMs: Math.max(1, Math.round(performance.now() - startedAt)),
        });
      };

      try {
        recorder.start();
        stopTimer = window.setTimeout(() => {
          if (recorder.state === "recording") {
            recorder.stop();
          }
        }, durationMs);
      } catch {
        settleFailure(new AudioEnvironmentError("음성 녹음을 시작할 수 없습니다."));
      }
    });
  } catch (cause) {
    stream.getTracks().forEach((track) => track.stop());
    if (cause instanceof AudioEnvironmentError) {
      throw cause;
    }
    throw new AudioEnvironmentError("음성 환경을 확인할 수 없습니다.");
  }
}

export function getSupportedWebmMimeType(): string | null {
  const candidates = ["audio/webm;codecs=opus", "audio/webm"];
  return (
    candidates.find((candidate) =>
      MediaRecorder.isTypeSupported?.(candidate),
    ) ?? null
  );
}
