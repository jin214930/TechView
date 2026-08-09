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
        message: "OK",
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
      Response.json(
        {
          code: 0,
          message: "Created",
          data: {
            resumed: false,
            sessionId: 10,
            turnId: 21,
            turnOrder: 1,
            turnType: "CORE",
            question: "Spring 설명을 해주세요.",
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
      question: "Spring 설명을 해주세요.",
    });
  });

  it("rejects an active response without its state flag", async () => {
    const api = createApi(
      Response.json({ code: 0, message: "OK", data: {} }),
    );

    await expect(api.getActive()).rejects.toThrow(
      "API response is missing active interview state",
    );
  });

  it("maps an owned interview progress response", async () => {
    const api = createApi(
      Response.json({
        code: 0,
        message: "OK",
        data: {
          sessionId: 10,
          status: "TURN_READY",
          turnId: 21,
          turnOrder: 1,
          turnType: "CORE",
          question: "서버에서 확인한 질문",
          errorCode: null,
          errorMessage: null,
        },
      }),
    );

    await expect(api.getProgress(10)).resolves.toEqual({
      sessionId: 10,
      status: "TURN_READY",
      turnId: 21,
      turnOrder: 1,
      turnType: "CORE",
      question: "서버에서 확인한 질문",
      errorCode: null,
      errorMessage: null,
    });
  });

  it("rejects a progress response with an unknown status", async () => {
    const api = createApi(
      Response.json({
        code: 0,
        message: "OK",
        data: { sessionId: 10, status: "UNKNOWN" },
      }),
    );

    await expect(api.getProgress(10)).rejects.toThrow(
      "API response has an invalid interview progress status",
    );
  });
});
