import type { TechViewApiClient } from "@/shared/api/client";
import { ApiClientError, unwrapApiResponse } from "@/shared/api/response";

export interface AnswerUploadRequest {
  audioContentType: "audio/webm";
  audioDurationMs: number;
}

export interface AnswerUploadPreparation {
  answerId: number;
  created: boolean;
  uploadUrl: string;
  uploadExpiresAt: string | null;
}

export interface AnswerUploadCompletion {
  answerId: number;
  processingJobId: number;
}

export interface InterviewVoiceApi {
  prepareAnswer(
    sessionId: number,
    turnId: number,
    request: AnswerUploadRequest,
  ): Promise<AnswerUploadPreparation>;
  uploadAudio(
    uploadUrl: string,
    blob: Blob,
    contentType: "audio/webm",
  ): Promise<void>;
  completeAnswerUpload(answerId: number): Promise<AnswerUploadCompletion>;
}

export function createInterviewVoiceApi(
  client: TechViewApiClient,
  uploadFetch: typeof fetch = globalThis.fetch,
): InterviewVoiceApi {
  return {
    async prepareAnswer(sessionId, turnId, request) {
      const result = await client.POST(
        "/api/v1/interviews/{sessionId}/turns/{turnId}/answers",
        {
          params: { path: { sessionId, turnId } },
          body: request,
        },
      );
      const data = unwrapApiResponse(result);

      return {
        answerId: requireNumber(data?.answerId, "answerId"),
        created: requireBoolean(data?.created, "created"),
        uploadUrl: requireString(data?.uploadUrl, "uploadUrl"),
        uploadExpiresAt: data?.uploadExpiresAt ?? null,
      };
    },

    async uploadAudio(uploadUrl, blob, contentType) {
      let response: Response;
      try {
        response = await uploadFetch(uploadUrl, {
          method: "PUT",
          headers: { "Content-Type": contentType },
          body: blob,
        });
      } catch {
        throw new ApiClientError(-1, 0, "음성 답변 업로드에 실패했습니다.");
      }

      if (!response.ok) {
        throw new ApiClientError(
          -1,
          response.status,
          "음성 답변 업로드에 실패했습니다.",
        );
      }
    },

    async completeAnswerUpload(answerId) {
      const result = await client.POST(
        "/api/v1/answers/{answerId}/upload-completion",
        { params: { path: { answerId } } },
      );
      const data = unwrapApiResponse(result);

      return {
        answerId: requireNumber(data?.answerId, "answerId"),
        processingJobId: requireNumber(data?.processingJobId, "processingJobId"),
      };
    },
  };
}

function requireBoolean(value: boolean | undefined, field: string): boolean {
  if (value === undefined) {
    throw new Error(`API response is missing ${field}`);
  }
  return value;
}

function requireNumber(value: number | undefined, field: string): number {
  if (value === undefined) {
    throw new Error(`API response is missing ${field}`);
  }
  return value;
}

function requireString(value: string | undefined, field: string): string {
  if (!value) {
    throw new Error(`API response is missing ${field}`);
  }
  return value;
}
