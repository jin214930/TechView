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

    fireEvent.click(screen.getByRole("button", { name: "답변 녹음하기" }));
    expect(
      await screen.findByRole("button", { name: "녹음 끝내기" }),
    ).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "녹음 끝내기" }));
    const submitButton = await screen.findByRole("button", {
      name: "답변 제출하기",
    });
    expect((submitButton as HTMLButtonElement).disabled).toBe(false);

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
    expect(await screen.findByText("답변을 제출했습니다.")).toBeTruthy();
  });

  it("keeps the recording available when upload fails", async () => {
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
    voiceApi.uploadAudio.mockRejectedValue(
      new Error("음성 답변 업로드에 실패했습니다."),
    );

    render(<InterviewAnswerTurn progress={progress} voiceApi={voiceApi} />);

    fireEvent.click(screen.getByRole("button", { name: "답변 녹음하기" }));
    fireEvent.click(await screen.findByRole("button", { name: "녹음 끝내기" }));
    fireEvent.click(
      await screen.findByRole("button", { name: "답변 제출하기" }),
    );

    expect(
      await screen.findByText("면접 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."),
    ).toBeTruthy();
    expect(
      (screen.getByRole("button", { name: "답변 제출하기" }) as HTMLButtonElement)
        .disabled,
    ).toBe(false);
  });
});
