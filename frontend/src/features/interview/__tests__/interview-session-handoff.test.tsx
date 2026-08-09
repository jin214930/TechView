import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { InterviewSessionHandoff } from "@/features/interview/ui/interview-session-handoff";

describe("InterviewSessionHandoff", () => {
  afterEach(() => {
    cleanup();
    window.sessionStorage.clear();
  });

  it("shows the prepared question for the matching session", async () => {
    window.sessionStorage.setItem(
      "techview.interview.start",
      JSON.stringify({
        resumed: false,
        sessionId: 10,
        turnId: 21,
        turnOrder: 1,
        turnType: "CORE",
        question: "Spring의 장점을 설명해 주세요.",
      }),
    );

    render(<InterviewSessionHandoff sessionId="10" />);

    expect(
      await screen.findByRole("heading", { name: "질문이 준비되었습니다." }),
    ).toBeTruthy();
    expect(screen.getByText("Spring의 장점을 설명해 주세요.")).toBeTruthy();
  });

  it("shows a recovery message when the session handoff is missing", async () => {
    render(<InterviewSessionHandoff sessionId="10" />);

    expect(
      await screen.findByRole("heading", {
        name: "면접 정보를 찾을 수 없습니다.",
      }),
    ).toBeTruthy();
  });
});
