import type { components } from "@/shared/api/schema";
import type { TechViewApiClient } from "@/shared/api/client";
import { unwrapApiResponse } from "@/shared/api/response";

export type FeedbackStatus = "PROCESSING" | "FAILED" | "COMPLETED";

export interface InterviewHistoryItem {
  sessionId: number;
  startedAt: string;
  completedAt: string | null;
  feedbackStatus: FeedbackStatus;
}

export interface InterviewHistoryPage {
  items: InterviewHistoryItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface CategoryFeedback {
  category: string;
  assessment: string;
  recommendation: string;
}

export interface InterviewFeedbackDetail {
  overallSummary: string;
  strengths: string[];
  improvements: string[];
  categoryFeedbacks: CategoryFeedback[];
  recommendedTopics: string[];
}

export interface InterviewFeedback {
  sessionId: number;
  status: FeedbackStatus;
  feedback: InterviewFeedbackDetail | null;
  errorMessage: string | null;
}

export interface InterviewFeedbackApi {
  getHistory(page: number, size: number): Promise<InterviewHistoryPage>;
  getFeedback(sessionId: number): Promise<InterviewFeedback>;
}

export function createInterviewFeedbackApi(
  client: TechViewApiClient,
): InterviewFeedbackApi {
  return {
    async getHistory(page, size) {
      const result = await client.GET("/api/v1/interviews", {
        params: { query: { page, size } },
      });
      const data = unwrapApiResponse(result);
      const items = data?.items ?? [];

      if (!Array.isArray(items)) {
        throw new Error("API response has an invalid interview history");
      }

      return {
        items: items.map(mapHistoryItem),
        page: requireNumber(data?.page, "page"),
        size: requireNumber(data?.size, "size"),
        totalElements: requireNumber(data?.totalElements, "totalElements"),
        totalPages: requireNumber(data?.totalPages, "totalPages"),
        hasNext: requireBoolean(data?.hasNext, "hasNext"),
      };
    },

    async getFeedback(sessionId) {
      const result = await client.GET("/api/v1/interviews/{sessionId}/feedback", {
        params: { path: { sessionId } },
      });
      const data = unwrapApiResponse(result);

      return {
        sessionId: requireNumber(data?.sessionId, "sessionId"),
        status: requireFeedbackStatus(data?.status),
        feedback: data?.feedback ? mapFeedbackDetail(data.feedback) : null,
        errorMessage: data?.errorMessage ?? null,
      };
    },
  };
}

function mapHistoryItem(
  item: components["schemas"]["InterviewHistoryItemResponse"],
): InterviewHistoryItem {
  return {
    sessionId: requireNumber(item.sessionId, "sessionId"),
    startedAt: requireString(item.startedAt, "startedAt"),
    completedAt: item.completedAt ?? null,
    feedbackStatus: requireFeedbackStatus(item.feedbackStatus),
  };
}

function mapFeedbackDetail(
  detail: components["schemas"]["InterviewFeedbackDetailResponse"],
): InterviewFeedbackDetail {
  return {
    overallSummary: requireString(detail.overallSummary, "overallSummary"),
    strengths: requireStringArray(detail.strengths, "strengths"),
    improvements: requireStringArray(detail.improvements, "improvements"),
    categoryFeedbacks: (detail.categoryFeedbacks ?? []).map((item) => ({
      category: requireString(item.category, "category"),
      assessment: requireString(item.assessment, "assessment"),
      recommendation: requireString(item.recommendation, "recommendation"),
    })),
    recommendedTopics: requireStringArray(
      detail.recommendedTopics,
      "recommendedTopics",
    ),
  };
}

function requireNumber(value: number | undefined, field: string): number {
  if (value === undefined) {
    throw new Error(`API response is missing ${field}`);
  }
  return value;
}

function requireBoolean(value: boolean | undefined, field: string): boolean {
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

function requireStringArray(
  value: string[] | undefined,
  field: string,
): string[] {
  if (!value) {
    throw new Error(`API response is missing ${field}`);
  }
  return value;
}

function requireFeedbackStatus(
  value: components["schemas"]["InterviewHistoryItemResponse"]["feedbackStatus"] |
    components["schemas"]["InterviewFeedbackResponse"]["status"],
): FeedbackStatus {
  if (value !== "PROCESSING" && value !== "FAILED" && value !== "COMPLETED") {
    throw new Error("API response has an invalid feedback status");
  }
  return value;
}
