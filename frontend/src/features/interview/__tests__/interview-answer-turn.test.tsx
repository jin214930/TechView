import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { StrictMode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { InterviewAnswerTurn } from "@/features/interview/ui/interview-answer-turn";
import { MicrophoneSetupRequiredError } from "@/features/interview/model/audio-environment";

const { startRecordingMock } = vi.hoisted(() => ({
  startRecordingMock: vi.fn(),
}));

vi.mock("@/features/interview/model/audio-answer-recorder", () => ({
  MAX_AUDIO_DURATION_MS: 180_000,
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

function createRecordingSession(recording: {
  blob: Blob;
  contentType: "audio/webm";
  durationMs: number;
}) {
  let resolveResult!: (value: typeof recording) => void;
  const result = new Promise<typeof recording>((resolve) => {
    resolveResult = resolve;
  });

  return {
    result,
    stop: vi.fn().mockImplementation(() => {
      resolveResult(recording);
      return result;
    }),
    cancel: vi.fn(),
    resolveResult,
  };
}

describe("InterviewAnswerTurn", () => {
  afterEach(() => {
    cleanup();
    startRecordingMock.mockReset();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it("records, previews, and submits a voice answer", async () => {
    const blob = new Blob(["audio"], { type: "audio/webm" });
    const recordingSession = createRecordingSession({
      blob,
      contentType: "audio/webm",
      durationMs: 1500,
    });
    startRecordingMock.mockResolvedValue(recordingSession);
    vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:answer");
    const voiceApi = createVoiceApi();

    render(<InterviewAnswerTurn progress={progress} voiceApi={voiceApi} />);

    const recordButton = getButton(0);
    fireEvent.click(recordButton);
    expect(getButton(0).disabled).toBe(false);
    expect(getButton(0).textContent).toContain("녹음 시작 취소");
    await waitFor(() =>
      expect(getButton(0).textContent).toContain("녹음 끝내기"),
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
    startRecordingMock.mockResolvedValue(createRecordingSession({
      blob,
      contentType: "audio/webm",
      durationMs: 1000,
    }));
    vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:answer");
    const voiceApi = createVoiceApi();
    voiceApi.uploadAudio
      .mockRejectedValueOnce(new Error("upload failed"))
      .mockResolvedValueOnce(undefined);

    render(<InterviewAnswerTurn progress={progress} voiceApi={voiceApi} />);

    fireEvent.click(getButton(0));
    await waitFor(() =>
      expect(getButton(0).textContent).toContain("녹음 끝내기"),
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

  it("shows the live recording duration while the answer is recording", async () => {
    vi.useFakeTimers();
    const blob = new Blob(["audio"], { type: "audio/webm" });
    const recordingSession = createRecordingSession({
      blob,
      contentType: "audio/webm",
      durationMs: 3000,
    });
    startRecordingMock.mockResolvedValue(recordingSession);

    render(<InterviewAnswerTurn progress={progress} voiceApi={createVoiceApi()} />);

    fireEvent.click(getButton(0));
    await act(async () => {
      await Promise.resolve();
    });
    expect(getButton(0).textContent).toContain("녹음 끝내기");
    expect(screen.getByText("00:00")).toBeTruthy();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(3000);
    });

    expect(screen.getByText("00:03")).toBeTruthy();
  });

  it("starts recording when React Strict Mode remounts effects", async () => {
    const blob = new Blob(["audio"], { type: "audio/webm" });
    const recordingSession = createRecordingSession({
      blob,
      contentType: "audio/webm",
      durationMs: 1000,
    });
    startRecordingMock.mockResolvedValue(recordingSession);

    render(
      <StrictMode>
        <InterviewAnswerTurn progress={progress} voiceApi={createVoiceApi()} />
      </StrictMode>,
    );

    fireEvent.click(getButton(0));

    await waitFor(() =>
      expect(getButton(0).textContent).toContain("녹음 끝내기"),
    );
    expect(recordingSession.cancel).not.toHaveBeenCalled();
  });

  it("offers the environment test again when the retained stream is missing", async () => {
    startRecordingMock.mockRejectedValue(new MicrophoneSetupRequiredError());

    render(<InterviewAnswerTurn progress={progress} voiceApi={createVoiceApi()} />);

    fireEvent.click(getButton(0));

    expect(
      (await screen.findByRole("link", {
        name: "음성 환경 다시 확인하기",
      })).getAttribute("href"),
    ).toBe("/interviews/setup");
  });

  it("can cancel a recording start that is waiting for microphone permission", async () => {
    let resolveRecorder!: (session: { cancel: () => void }) => void;
    startRecordingMock.mockReturnValue(
      new Promise((resolve) => {
        resolveRecorder = resolve;
      }),
    );

    render(<InterviewAnswerTurn progress={progress} voiceApi={createVoiceApi()} />);

    fireEvent.click(getButton(0));
    expect(getButton(0).disabled).toBe(false);
    expect(getButton(0).textContent).toContain("녹음 시작 취소");

    fireEvent.click(getButton(0));

    await waitFor(() =>
      expect(getButton(0).textContent).toContain("답변 녹음하기"),
    );
    expect(screen.getByRole("alert").textContent).toContain(
      "다시 녹음해 주세요.",
    );

    const cancel = vi.fn();
    resolveRecorder({ cancel });
    await waitFor(() => expect(cancel).toHaveBeenCalledOnce());
  });

  it("moves to the preview state when the recorder stops automatically", async () => {
    const blob = new Blob(["audio"], { type: "audio/webm" });
    const recordingSession = createRecordingSession({
      blob,
      contentType: "audio/webm",
      durationMs: 180000,
    });
    startRecordingMock.mockResolvedValue(recordingSession);
    vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:answer");

    render(<InterviewAnswerTurn progress={progress} voiceApi={createVoiceApi()} />);

    fireEvent.click(getButton(0));
    await waitFor(() =>
      expect(getButton(0).textContent).toContain("녹음 끝내기"),
    );
    recordingSession.resolveResult({
      blob,
      contentType: "audio/webm",
      durationMs: 180000,
    });

    await waitFor(() => expect(getButton(1).disabled).toBe(false));
    expect(screen.getByText("녹음 시간 180초")).toBeTruthy();
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
