import { describe, expect, it, vi } from "vitest";

import { createInterviewFeedbackApi } from "@/features/interview/api/interview-feedback-api";
import { createTechViewApiClient } from "@/shared/api/client";

function createApi(response: Response) {
  return createInterviewFeedbackApi(
    createTechViewApiClient({
      baseUrl: "http://localhost:8080",
      fetch: vi.fn().mockResolvedValue(response),
    }),
  );
}

describe("interviewFeedbackApi", () => {
  it("maps paginated interview history", async () => {
    const api = createApi(
      Response.json({
        code: 0,
        message: "OK",
        data: {
          items: [
            {
              sessionId: 10,
              startedAt: "2026-08-09T10:00:00+09:00",
              completedAt: "2026-08-09T10:15:00+09:00",
              feedbackStatus: "COMPLETED",
            },
          ],
          page: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
          hasNext: false,
        },
      }),
    );

    await expect(api.getHistory(0, 10)).resolves.toEqual({
      items: [
        {
          sessionId: 10,
          startedAt: "2026-08-09T10:00:00+09:00",
          completedAt: "2026-08-09T10:15:00+09:00",
          feedbackStatus: "COMPLETED",
        },
      ],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
    });
  });

  it("maps a processing feedback response without detail", async () => {
    const api = createApi(
      Response.json({
        code: 0,
        message: "OK",
        data: {
          sessionId: 10,
          status: "PROCESSING",
          errorMessage: null,
        },
      }),
    );

    await expect(api.getFeedback(10)).resolves.toEqual({
      sessionId: 10,
      status: "PROCESSING",
      feedback: null,
      errorMessage: null,
    });
  });

  it("maps completed category feedback detail", async () => {
    const api = createApi(
      Response.json({
        code: 0,
        message: "OK",
        data: {
          sessionId: 10,
          status: "COMPLETED",
          feedback: {
            overallSummary: "좋은 답변입니다.",
            strengths: ["핵심을 잘 설명했습니다."],
            improvements: ["예시를 더 추가해 보세요."],
            categoryFeedbacks: [
              {
                category: "JAVA",
                assessment: "기본기가 좋습니다.",
                recommendation: "동시성 학습을 이어가세요.",
              },
            ],
            recommendedTopics: ["동시성"],
          },
          errorMessage: null,
        },
      }),
    );

    await expect(api.getFeedback(10)).resolves.toMatchObject({
      sessionId: 10,
      status: "COMPLETED",
      feedback: {
        overallSummary: "좋은 답변입니다.",
        categoryFeedbacks: [
          expect.objectContaining({ category: "JAVA" }),
        ],
      },
    });
  });
});
