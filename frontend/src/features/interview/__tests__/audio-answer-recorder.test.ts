import { afterEach, describe, expect, it, vi } from "vitest";

import {
  MAX_AUDIO_DURATION_MS,
  startAudioAnswerRecording,
} from "@/features/interview/model/audio-answer-recorder";
import {
  AudioEnvironmentError,
  releaseRetainedMicrophoneStream,
  startMicrophoneEnvironmentTest,
} from "@/features/interview/model/audio-environment";

class FakeMediaRecorder {
  static isTypeSupported = vi.fn().mockReturnValue(true);
  static lastInstance: FakeMediaRecorder | null = null;

  readonly mimeType = "audio/webm";
  state: RecordingState = "inactive";
  ondataavailable: ((event: BlobEvent) => void) | null = null;
  onerror: (() => void) | null = null;
  onstop: (() => void) | null = null;

  constructor(private readonly stream: MediaStream) {
    FakeMediaRecorder.lastInstance = this;
  }

  start() {
    this.state = "recording";
    this.ondataavailable?.(
      new BlobEvent("dataavailable", {
        data: new Blob(["answer"], { type: "audio/webm" }),
      }),
    );
  }

  stop() {
    this.state = "inactive";
    this.onstop?.();
  }
}

async function prepareRetainedStream() {
  const microphoneTest = await startMicrophoneEnvironmentTest();
  microphoneTest.retainStream();
  await microphoneTest.stop();
}

describe("startAudioAnswerRecording", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    Reflect.deleteProperty(navigator, "mediaDevices");
    releaseRetainedMicrophoneStream();
    FakeMediaRecorder.isTypeSupported.mockReturnValue(true);
    FakeMediaRecorder.lastInstance = null;
    vi.useRealTimers();
  });

  it("returns a WebM recording without releasing the shared microphone stream", async () => {
    const stopTrack = vi.fn();
    const stream = {
      getTracks: () => [{ stop: stopTrack }],
    } as unknown as MediaStream;
    const getUserMedia = vi.fn().mockResolvedValue(stream);

    vi.stubGlobal("MediaRecorder", FakeMediaRecorder);
    Object.defineProperty(navigator, "mediaDevices", {
      configurable: true,
      value: { getUserMedia },
    });

    await prepareRetainedStream();
    const session = await startAudioAnswerRecording();
    const result = await session.stop();

    expect(getUserMedia).toHaveBeenCalledWith({ audio: true });
    expect(result.contentType).toBe("audio/webm");
    expect(result.blob.type).toBe("audio/webm");
    expect(result.blob.size).toBeGreaterThan(0);
    expect(result.durationMs).toBeGreaterThan(0);
    expect(stopTrack).not.toHaveBeenCalled();
  });

  it("does not request microphone permission again without the environment test", async () => {
    const getUserMedia = vi.fn();
    vi.stubGlobal("MediaRecorder", FakeMediaRecorder);
    Object.defineProperty(navigator, "mediaDevices", {
      configurable: true,
      value: { getUserMedia },
    });

    await expect(startAudioAnswerRecording()).rejects.toEqual(
      new AudioEnvironmentError(
        "면접 환경 테스트를 먼저 완료한 후 답변을 녹음해 주세요.",
      ),
    );
    expect(getUserMedia).not.toHaveBeenCalled();
  });

  it("automatically stops at the server audio duration limit", async () => {
    vi.useFakeTimers();
    const stopTrack = vi.fn();
    const stream = {
      getTracks: () => [{ stop: stopTrack }],
    } as unknown as MediaStream;
    const getUserMedia = vi.fn().mockResolvedValue(stream);

    vi.stubGlobal("MediaRecorder", FakeMediaRecorder);
    Object.defineProperty(navigator, "mediaDevices", {
      configurable: true,
      value: { getUserMedia },
    });

    await prepareRetainedStream();
    const session = await startAudioAnswerRecording();
    await vi.advanceTimersByTimeAsync(MAX_AUDIO_DURATION_MS);

    expect(FakeMediaRecorder.lastInstance?.state).toBe("inactive");
    const result = await session.result;
    expect(result.contentType).toBe("audio/webm");
    expect(stopTrack).not.toHaveBeenCalled();
  });

  it("reuses the microphone stream granted during the environment test", async () => {
    const stopTrack = vi.fn();
    const stream = {
      getTracks: () => [{ stop: stopTrack, readyState: "live" }],
    } as unknown as MediaStream;
    const getUserMedia = vi.fn().mockResolvedValue(stream);

    vi.stubGlobal("MediaRecorder", FakeMediaRecorder);
    Object.defineProperty(navigator, "mediaDevices", {
      configurable: true,
      value: { getUserMedia },
    });

    const microphoneTest = await startMicrophoneEnvironmentTest();
    microphoneTest.retainStream();
    await microphoneTest.stop();

    const answerSession = await startAudioAnswerRecording();
    await answerSession.stop();

    expect(getUserMedia).toHaveBeenCalledOnce();
    expect(stopTrack).not.toHaveBeenCalled();
  });
});
