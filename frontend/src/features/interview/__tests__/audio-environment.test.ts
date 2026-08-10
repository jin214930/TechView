import { afterEach, describe, expect, it, vi } from "vitest";

import {
  AudioEnvironmentError,
  startMicrophoneEnvironmentTest,
  testMicrophoneEnvironment,
} from "@/features/interview/model/audio-environment";

class FakeMediaRecorder {
  static isTypeSupported = vi.fn().mockReturnValue(true);

  readonly mimeType = "audio/webm";
  state: RecordingState = "inactive";
  ondataavailable: ((event: BlobEvent) => void) | null = null;
  onerror: (() => void) | null = null;
  onstop: (() => void) | null = null;

  constructor(private readonly stream: MediaStream) {}

  start() {
    this.state = "recording";
    this.ondataavailable?.(
      new BlobEvent("dataavailable", {
        data: new Blob(["test audio"], { type: "audio/webm" }),
      }),
    );
  }

  stop() {
    this.state = "inactive";
    this.onstop?.();
  }
}

describe("testMicrophoneEnvironment", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    Reflect.deleteProperty(navigator, "mediaDevices");
    FakeMediaRecorder.isTypeSupported.mockReturnValue(true);
  });

  it("records a short sample and stops every microphone track", async () => {
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

    const resultPromise = testMicrophoneEnvironment(1000);
    await vi.advanceTimersByTimeAsync(1000);
    const result = await resultPromise;

    expect(getUserMedia).toHaveBeenCalledWith({ audio: true });
    expect(result.blob.type).toBe("audio/webm");
    expect(result.blob.size).toBeGreaterThan(0);
    expect(result.durationMs).toBeGreaterThan(0);
    expect(stopTrack).toHaveBeenCalledOnce();
  });

  it("can be stopped manually before the maximum duration", async () => {
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

    const session = await startMicrophoneEnvironmentTest(60_000);
    await vi.advanceTimersByTimeAsync(1500);
    const result = await session.stop();

    expect(result.blob.type).toBe("audio/webm");
    expect(result.durationMs).toBeGreaterThan(0);
    expect(stopTrack).toHaveBeenCalledOnce();

    await vi.advanceTimersByTimeAsync(60_000);
    expect(stopTrack).toHaveBeenCalledOnce();
  });

  it("rejects when the browser does not expose microphone access", async () => {
    Object.defineProperty(navigator, "mediaDevices", {
      configurable: true,
      value: undefined,
    });

    await expect(testMicrophoneEnvironment()).rejects.toEqual(
      new AudioEnvironmentError("이 브라우저에서는 마이크를 사용할 수 없습니다."),
    );
  });

  it("rejects when the browser cannot record WebM", async () => {
    const getUserMedia = vi.fn();
    FakeMediaRecorder.isTypeSupported.mockReturnValue(false);
    vi.stubGlobal("MediaRecorder", FakeMediaRecorder);
    Object.defineProperty(navigator, "mediaDevices", {
      configurable: true,
      value: { getUserMedia },
    });

    await expect(testMicrophoneEnvironment()).rejects.toEqual(
      new AudioEnvironmentError(
        "이 브라우저에서는 audio/webm 음성 녹음을 지원하지 않습니다.",
      ),
    );
    expect(getUserMedia).not.toHaveBeenCalled();
  });

  it("turns a denied microphone permission into a user-facing error", async () => {
    const getUserMedia = vi
      .fn()
      .mockRejectedValue(new DOMException("denied", "NotAllowedError"));
    Object.defineProperty(navigator, "mediaDevices", {
      configurable: true,
      value: { getUserMedia },
    });
    vi.stubGlobal("MediaRecorder", FakeMediaRecorder);

    await expect(testMicrophoneEnvironment()).rejects.toEqual(
      new AudioEnvironmentError(
        "마이크 권한을 허용해야 면접을 진행할 수 있습니다.",
      ),
    );
  });
});
