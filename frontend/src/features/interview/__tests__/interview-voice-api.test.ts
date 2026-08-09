import { describe, expect, it, vi } from "vitest";

import { createTechViewApiClient } from "@/shared/api/client";
import { createInterviewVoiceApi } from "@/features/interview/api/interview-voice-api";

function createApi(responses: Response[]) {
  const apiFetch = vi.fn().mockImplementation(() => responses.shift());
  return {
    api: createInterviewVoiceApi(
      createTechViewApiClient({
        baseUrl: "http://localhost:8080",
        fetch: apiFetch,
      }),
    ),
    apiFetch,
  };
}

describe("interviewVoiceApi", () => {
  it("maps answer upload preparation", async () => {
    const { api } = createApi([
      Response.json(
        {
          code: 0,
          message: "Created",
          data: {
            answerId: 30,
            created: true,
            uploadUrl: "http://minio.local/upload",
            uploadExpiresAt: "2026-08-09T04:00:00Z",
          },
        },
        { status: 201 },
      ),
    ]);

    await expect(
      api.prepareAnswer(10, 21, {
        audioContentType: "audio/webm",
        audioDurationMs: 1500,
      }),
    ).resolves.toEqual({
      answerId: 30,
      created: true,
      uploadUrl: "http://minio.local/upload",
      uploadExpiresAt: "2026-08-09T04:00:00Z",
    });
  });

  it("uploads the blob to the presigned URL with the WebM content type", async () => {
    const uploadFetch = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));
    const voiceApi = createInterviewVoiceApi(
      createTechViewApiClient({ baseUrl: "http://localhost:8080" }),
      uploadFetch,
    );
    const blob = new Blob(["audio"], { type: "audio/webm" });

    await voiceApi.uploadAudio("http://minio.local/upload", blob, "audio/webm");

    expect(uploadFetch).toHaveBeenCalledWith("http://minio.local/upload", {
      method: "PUT",
      headers: { "Content-Type": "audio/webm" },
      body: blob,
    });
  });

  it("maps upload completion", async () => {
    const { api } = createApi([
      Response.json(
        {
          code: 0,
          message: "Accepted",
          data: { answerId: 30, processingJobId: 40 },
        },
        { status: 202 },
      ),
    ]);

    await expect(api.completeAnswerUpload(30)).resolves.toEqual({
      answerId: 30,
      processingJobId: 40,
    });
  });

  it("rejects an unsuccessful presigned upload", async () => {
    const uploadFetch = vi.fn().mockResolvedValue(new Response(null, { status: 500 }));
    const api = createInterviewVoiceApi(
      createTechViewApiClient({ baseUrl: "http://localhost:8080" }),
      uploadFetch,
    );

    await expect(
      api.uploadAudio(
        "http://minio.local/upload",
        new Blob(["audio"], { type: "audio/webm" }),
        "audio/webm",
      ),
    ).rejects.toThrow("음성 답변 업로드에 실패했습니다.");
  });
});
