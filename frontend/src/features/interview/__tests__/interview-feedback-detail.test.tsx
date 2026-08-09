import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { AuthApi } from "@/features/auth/api/auth-api";
import { AuthProviderCore } from "@/features/auth/model/auth-context";
import type { InterviewFeedbackApi } from "@/features/interview/api/interview-feedback-api";
import { InterviewFeedbackDetail } from "@/features/interview/ui/interview-feedback-detail";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
}));

const member = {
  id: 1,
  email: "user@example.com",
  nickname: "테스트 사용자",
};

function createAuthApi(): AuthApi {
  return {
    login: vi.fn(),
    signUp: vi.fn(),
    refresh: vi.fn().mockResolvedValue("access-token"),
    getMe: vi.fn().mockResolvedValue(member),
    logout: vi.fn(),
  };
}

function createApi(result: Awaited<ReturnType<InterviewFeedbackApi["getFeedback"]>>): InterviewFeedbackApi {
  return {
    getHistory: vi.fn(),
    getFeedback: vi.fn().mockResolvedValue(result),
  };
}

describe("InterviewFeedbackDetail", () => {
  afterEach(() => cleanup());

  it("renders completed feedback detail", async () => {
    const api = createApi({
      sessionId: 10,
      status: "COMPLETED",
      feedback: {
        overallSummary: "전체적으로 좋은 답변입니다.",
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
    });

    render(
      <AuthProviderCore api={createAuthApi()}>
        <InterviewFeedbackDetail sessionId="10" api={api} />
      </AuthProviderCore>,
    );

    expect(await screen.findByText("JAVA")).toBeTruthy();
  });

  it("shows status-specific feedback messages", async () => {
    const api = createApi({
      sessionId: 10,
      status: "FAILED",
      feedback: null,
      errorMessage: "피드백 생성에 실패했습니다.",
    });

    render(
      <AuthProviderCore api={createAuthApi()}>
        <InterviewFeedbackDetail sessionId="10" api={api} />
      </AuthProviderCore>,
    );

    expect(await screen.findByRole("alert")).toBeTruthy();
  });
});
