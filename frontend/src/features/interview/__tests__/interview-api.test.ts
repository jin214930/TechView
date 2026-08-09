import { describe, expect, it, vi } from "vitest";

import { createInterviewApi } from "@/features/interview/api/interview-api";
import { createTechViewApiClient } from "@/shared/api/client";

function createApi(response: Response) {
  return createInterviewApi(
    createTechViewApiClient({
      baseUrl: "http://localhost:8080",
      fetch: vi.fn().mockResolvedValue(response),
    }),
  );
}

describe("interviewApi", () => {
  it("maps an empty active interview response", async () => {
    const api = createApi(
      Response.json({
        code: 0,
        message: "요청에 성공했습니다.",
        data: {
          hasActiveSession: false,
          sessionId: null,
          startedAt: null,
          currentTurnOrder: null,
        },
      }),
    );

    await expect(api.getActive()).resolves.toEqual({
      hasActiveSession: false,
      sessionId: null,
      startedAt: null,
      currentTurnOrder: null,
    });
  });

  it("maps a new interview start response", async () => {
    const api = createApi(
      Response.json({
        code: 0,
        message: "면접이 시작되었습니다.",
        data: {
          resumed: false,
          sessionId: 10,
          turnId: 21,
          turnOrder: 1,
          turnType: "CORE",
          question: "Spring의 장점을 설명해 주세요.",
        },
      },
        { status: 201 },
      ),
    );

    await expect(api.startOrResume()).resolves.toEqual({
      resumed: false,
      sessionId: 10,
      turnId: 21,
      turnOrder: 1,
      turnType: "CORE",
      question: "Spring의 장점을 설명해 주세요.",
    });
  });

  it("rejects an active response without its state flag", async () => {
    const api = createApi(
      Response.json({ code: 0, message: "요청에 성공했습니다.", data: {} }),
    );

    await expect(api.getActive()).rejects.toThrow(
      "API response is missing active interview state",
    );
  });
});
