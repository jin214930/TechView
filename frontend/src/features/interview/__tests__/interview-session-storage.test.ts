import { afterEach, describe, expect, it } from "vitest";

import {
  clearInterviewStart,
  INTERVIEW_START_STORAGE_KEY,
  saveInterviewStart,
} from "@/features/interview/model/interview-session-storage";

describe("interview session storage", () => {
  afterEach(() => {
    window.sessionStorage.clear();
  });

  it("stores the start response for the next interview route", () => {
    const start = {
      resumed: false,
      sessionId: 10,
      turnId: 21,
      turnOrder: 1,
      turnType: "CORE" as const,
      question: "Spring의 장점을 설명해 주세요.",
    };

    saveInterviewStart(start);

    expect(window.sessionStorage.getItem(INTERVIEW_START_STORAGE_KEY)).toBe(
      JSON.stringify(start),
    );
  });

  it("clears the handoff after the client session ends", () => {
    window.sessionStorage.setItem(INTERVIEW_START_STORAGE_KEY, "stale-handoff");

    clearInterviewStart();

    expect(window.sessionStorage.getItem(INTERVIEW_START_STORAGE_KEY)).toBeNull();
  });
});
