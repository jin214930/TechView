import type { components } from "@/shared/api/schema";
import type { TechViewApiClient } from "@/shared/api/client";
import { unwrapApiResponse } from "@/shared/api/response";
import type {
  ActiveInterview,
  InterviewProgress,
  InterviewProgressStatus,
  InterviewStart,
  InterviewTurnType,
} from "@/features/interview/model/interview-types";

export interface InterviewApi {
  getActive(): Promise<ActiveInterview>;
  startOrResume(): Promise<InterviewStart>;
  getProgress(sessionId: number): Promise<InterviewProgress>;
}

export function createInterviewApi(client: TechViewApiClient): InterviewApi {
  return {
    async getActive() {
      const result = await client.GET("/api/v1/interviews/active");
      const data = unwrapApiResponse(result);

      if (!data || typeof data.hasActiveSession !== "boolean") {
        throw new Error("API response is missing active interview state");
      }

      return {
        hasActiveSession: data.hasActiveSession,
        sessionId: data.sessionId ?? null,
        startedAt: data.startedAt ?? null,
        currentTurnOrder: data.currentTurnOrder ?? null,
      };
    },

    async startOrResume() {
      const result = await client.POST("/api/v1/interviews");
      const data = unwrapApiResponse(result);

      return {
        resumed: requireBoolean(data?.resumed, "resumed"),
        sessionId: requireNumber(data?.sessionId, "sessionId"),
        turnId: requireNumber(data?.turnId, "turnId"),
        turnOrder: requireNumber(data?.turnOrder, "turnOrder"),
        turnType: requireTurnType(data?.turnType),
        question: requireString(data?.question, "question"),
      };
    },

    async getProgress(sessionId) {
      const result = await client.GET(
        "/api/v1/interviews/{sessionId}/progress",
        { params: { path: { sessionId } } },
      );
      const data = unwrapApiResponse(result);

      return {
        sessionId: requireNumber(data?.sessionId, "sessionId"),
        status: requireProgressStatus(data?.status),
        turnId: optionalNumber(data?.turnId),
        turnOrder: optionalNumber(data?.turnOrder),
        turnType: optionalTurnType(data?.turnType),
        question: data?.question ?? null,
        errorCode: data?.errorCode ?? null,
        errorMessage: data?.errorMessage ?? null,
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

function requireTurnType(
  value: components["schemas"]["InterviewStartResponse"]["turnType"],
): InterviewTurnType {
  if (value !== "CORE" && value !== "FOLLOW_UP") {
    throw new Error("API response has an invalid turnType");
  }
  return value;
}

function requireProgressStatus(
  value: components["schemas"]["InterviewProgressResponse"]["status"],
): InterviewProgressStatus {
  if (
    value !== "PROCESSING" &&
    value !== "TURN_READY" &&
    value !== "COMPLETED" &&
    value !== "FAILED"
  ) {
    throw new Error("API response has an invalid interview progress status");
  }
  return value;
}

function optionalNumber(value: number | null | undefined): number | null {
  if (value === null || value === undefined) {
    return null;
  }
  if (typeof value !== "number") {
    throw new Error("API response has an invalid numeric field");
  }
  return value;
}

function optionalTurnType(
  value: components["schemas"]["InterviewProgressResponse"]["turnType"],
): InterviewTurnType | null {
  if (value === null || value === undefined) {
    return null;
  }
  return requireTurnType(value);
}
