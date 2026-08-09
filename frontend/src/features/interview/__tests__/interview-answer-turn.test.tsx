import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { InterviewAnswerTurn } from "@/features/interview/ui/interview-answer-turn";

const { startRecordingMock } = vi.hoisted(() => ({
  startRecordingMock: vi.fn(),
}));

vi.mock("@/features/interview/model/audio-answer-recorder", () => ({
  startAudioAnswerRecording: startRecordingMock,
}));

const progress = {
  sessionId: 10,
  status: "TURN_READY" as const,
  turnId: 21,
  turnOrder: 1,
  turnType: "CORE" as const,
  question: "Spring 설명을 해주세요.",
  errorCode: null,
  errorMessage: null,
};

function createVoiceApi() {
  return {
    prepareAnswer: vi.fn().mockResolvedValue({
      answerId: 30,
      created: true,
      uploadUrl: "http://minio.local/upload",
      uploadExpiresAt: null,
    }),
    uploadAudio: vi.fn().mockResolvedValue(undefined),
    completeAnswerUpload: vi.fn().mockResolvedValue({
      answerId: 30,
      processingJobId: 40,
    }),
  };
}

function getButton(index: number): HTMLButtonElement {
  const button = screen.getAllByRole("button")[index];
  if (!(button instanceof HTMLButtonElement)) {
    throw new Error(`Expected button at index ${index}`);
  }
  return button;
}

describe("InterviewAnswerTurn", () => {
  afterEach(() => {
    cleanup();
    startRecordingMock.mockReset();
    vi.restoreAllMocks();
  });

  it("records, previews, and submits a voice answer", async () => {
    const blob = new Blob(["audio"], { type: "audio/webm" });
    const recordingSession = {
      stop: vi.fn().mockResolvedValue({
        blob,
        contentType: "audio/webm" as const,
        durationMs: 1500,
      }),
      cancel: vi.fn(),
    };
    startRecordingMock.mockResolvedValue(recordingSession);
    vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:answer");
    const voiceApi = createVoiceApi();

    render(<InterviewAnswerTurn progress={progress} voiceApi={voiceApi} />);

    const recordButton = getButton(0);
    fireEvent.click(recordButton);
    await waitFor(() =>
      expect(getButton(0).className).toContain("bg-rose-600"),
    );

    fireEvent.click(getButton(0));
    const submitButton = getButton(1);
    await waitFor(() => expect(submitButton.disabled).toBe(false));

    fireEvent.click(submitButton);

    await waitFor(() =>
      expect(voiceApi.completeAnswerUpload).toHaveBeenCalledWith(30),
    );
    expect(voiceApi.prepareAnswer).toHaveBeenCalledWith(10, 21, {
      audioContentType: "audio/webm",
      audioDurationMs: 1500,
    });
    expect(voiceApi.uploadAudio).toHaveBeenCalledWith(
      "http://minio.local/upload",
      blob,
      "audio/webm",
    );
    expect(screen.getByRole("status")).toBeTruthy();
  });

  it("keeps the prepared recording retryable and disables re-recording after upload fails", async () => {
    const blob = new Blob(["audio"], { type: "audio/webm" });
    startRecordingMock.mockResolvedValue({
      stop: vi.fn().mockResolvedValue({
        blob,
        contentType: "audio/webm" as const,
        durationMs: 1000,
      }),
      cancel: vi.fn(),
    });
    vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:answer");
    const voiceApi = createVoiceApi();
    voiceApi.uploadAudio
      .mockRejectedValueOnce(new Error("upload failed"))
      .mockResolvedValueOnce(undefined);

    render(<InterviewAnswerTurn progress={progress} voiceApi={voiceApi} />);

    fireEvent.click(getButton(0));
    await waitFor(() =>
      expect(getButton(0).className).toContain("bg-rose-600"),
    );
    fireEvent.click(getButton(0));
    await waitFor(() =>
      expect(getButton(1).disabled).toBe(false),
    );
    fireEvent.click(getButton(1));

    expect(await screen.findByRole("alert")).toBeTruthy();
    expect(getButton(0).disabled).toBe(true);

    fireEvent.click(getButton(1));

    expect(await screen.findByRole("status")).toBeTruthy();
    expect(voiceApi.prepareAnswer).toHaveBeenCalledTimes(2);
    expect(voiceApi.prepareAnswer).toHaveBeenNthCalledWith(2, 10, 21, {
      audioContentType: "audio/webm",
      audioDurationMs: 1000,
    });
    expect(voiceApi.uploadAudio).toHaveBeenCalledTimes(2);
  });

  it("cancels a recorder that resolves after the component unmounts", async () => {
    let resolveRecorder!: (session: { cancel: () => void }) => void;
    startRecordingMock.mockReturnValue(
      new Promise((resolve) => {
        resolveRecorder = resolve;
      }),
    );

    const { unmount } = render(
      <InterviewAnswerTurn progress={progress} voiceApi={createVoiceApi()} />,
    );
    fireEvent.click(getButton(0));
    unmount();

    const cancel = vi.fn();
    resolveRecorder({ cancel });

    await waitFor(() => expect(cancel).toHaveBeenCalledOnce());
  });
});
