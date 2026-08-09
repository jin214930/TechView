import type { components } from "@/shared/api/schema";
import type { TechViewApiClient } from "@/shared/api/client";
import { unwrapApiResponse } from "@/shared/api/response";
import type {
  ActiveInterview,
  InterviewStart,
  InterviewTurnType,
} from "@/features/interview/model/interview-types";

export interface InterviewApi {
  getActive(): Promise<ActiveInterview>;
  startOrResume(): Promise<InterviewStart>;
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
