import { describe, expectTypeOf, it } from "vitest";

import type {
  components,
  operations,
} from "@/shared/api/schema";

describe("generated OpenAPI schema", () => {
  it("keeps authentication request and response types", () => {
    expectTypeOf<components["schemas"]["LoginRequest"]>().toEqualTypeOf<{
      email: string;
      password: string;
    }>();

    expectTypeOf<
      operations["login"]["responses"][200]["content"]["*/*"]
    >().toEqualTypeOf<components["schemas"]["ApiResponseLoginResponse"]>();
  });

  it("keeps interview and answer success response types", () => {
    expectTypeOf<
      operations["startOrResume"]["responses"][201]["content"]["*/*"]
    >().toEqualTypeOf<
      components["schemas"]["ApiResponseInterviewStartResponse"]
    >();

    expectTypeOf<
      operations["completeAnswerUpload"]["responses"][202]["content"]["*/*"]
    >().toEqualTypeOf<
      components["schemas"]["ApiResponseAnswerUploadCompletionResponse"]
    >();
  });

  it("requires all answer upload preparation fields", () => {
    expectTypeOf<components["schemas"]["AnswerCreateRequest"]>().toEqualTypeOf<{
      audioContentType: string;
      audioDurationMs: number;
    }>();
  });

  it("keeps common error fields typed", () => {
    expectTypeOf<components["schemas"]["ApiErrorResponse"]>().toEqualTypeOf<{
      code?: number;
      message?: string;
    }>();
  });

  it("keeps feedback detail response types", () => {
    expectTypeOf<
      operations["getFeedback"]["responses"][200]["content"]["*/*"]
    >().toEqualTypeOf<
      components["schemas"]["ApiResponseInterviewFeedbackResponse"]
    >();
  });
});
