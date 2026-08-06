import { describe, expect, it } from "vitest";

import {
  ApiClientError,
  unwrapApiResponse,
} from "@/shared/api/response";

describe("unwrapApiResponse", () => {
  it("returns data from a successful API response", () => {
    const data = unwrapApiResponse({
      data: {
        code: 0,
        message: "요청에 성공했습니다.",
        data: { memberId: 1 },
      },
      response: new Response(null, { status: 200 }),
    });

    expect(data).toEqual({ memberId: 1 });
  });

  it("supports successful responses without data", () => {
    const data = unwrapApiResponse({
      data: {
        code: 0,
        message: "로그아웃이 완료되었습니다.",
      },
      response: new Response(null, { status: 200 }),
    });

    expect(data).toBeUndefined();
  });

  it("throws a typed error for an HTTP error response", () => {
    try {
      unwrapApiResponse({
        error: {
          code: 2001,
          message: "로그인에 실패했습니다.",
        },
        response: new Response(null, { status: 401 }),
      });
      expect.fail("ApiClientError should have been thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(ApiClientError);
      expect(error).toMatchObject({
        code: 2001,
        status: 401,
        message: "로그인에 실패했습니다.",
      });
    }
  });

  it("throws when a successful HTTP response contains a domain error code", () => {
    expect(() =>
      unwrapApiResponse({
        data: {
          code: 3001,
          message: "면접을 시작할 수 없습니다.",
        },
        response: new Response(null, { status: 200 }),
      }),
    ).toThrow(ApiClientError);
  });
});
