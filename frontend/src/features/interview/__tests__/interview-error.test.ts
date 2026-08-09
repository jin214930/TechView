import { describe, expect, it } from "vitest";

import { AudioEnvironmentError } from "@/features/interview/model/audio-environment";
import { getInterviewErrorMessage } from "@/features/interview/model/interview-error";

describe("getInterviewErrorMessage", () => {
  it("keeps technical errors behind the localized fallback", () => {
    expect(getInterviewErrorMessage(new TypeError("Failed to fetch"))).toBe(
      "면접 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
    );
  });

  it("exposes typed audio environment errors to the user", () => {
    expect(
      getInterviewErrorMessage(
        new AudioEnvironmentError(
          "이 브라우저에서는 audio/webm 음성 녹음을 지원하지 않습니다.",
        ),
      ),
    ).toBe("이 브라우저에서는 audio/webm 음성 녹음을 지원하지 않습니다.");
  });
});
