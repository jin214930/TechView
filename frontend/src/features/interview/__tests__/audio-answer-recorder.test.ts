import { afterEach, describe, expect, it, vi } from "vitest";

import { startAudioAnswerRecording } from "@/features/interview/model/audio-answer-recorder";

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
        data: new Blob(["answer"], { type: "audio/webm" }),
      }),
    );
  }

  stop() {
    this.state = "inactive";
    this.onstop?.();
  }
}

describe("startAudioAnswerRecording", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    Reflect.deleteProperty(navigator, "mediaDevices");
    FakeMediaRecorder.isTypeSupported.mockReturnValue(true);
  });

  it("returns a WebM recording and releases microphone tracks on stop", async () => {
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

    const session = await startAudioAnswerRecording();
    const result = await session.stop();

    expect(getUserMedia).toHaveBeenCalledWith({ audio: true });
    expect(result.contentType).toBe("audio/webm");
    expect(result.blob.type).toBe("audio/webm");
    expect(result.blob.size).toBeGreaterThan(0);
    expect(result.durationMs).toBeGreaterThan(0);
    expect(stopTrack).toHaveBeenCalledOnce();
  });
});
