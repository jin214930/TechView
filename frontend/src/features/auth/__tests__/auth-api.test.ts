import { describe, expect, it, vi } from "vitest";

import { createAuthApi } from "@/features/auth/api/auth-api";
import { createTechViewApiClient } from "@/shared/api/client";

describe("authApi", () => {
  it("logs in and maps the token and member", async () => {
    const fetchMock = vi.fn(async (request: Request) => {
      expect(request.url).toBe("http://localhost:8080/api/v1/auth/login");
      expect(await request.json()).toEqual({
        email: "user@example.com",
        password: "password123!",
      });

      return Response.json({
        code: 0,
        message: "로그인이 완료되었습니다.",
        data: {
          accessToken: "access-token",
          memberId: 1,
          email: "user@example.com",
          nickname: "테크뷰",
        },
      });
    });
    const api = createAuthApi(
      createTechViewApiClient({
        baseUrl: "http://localhost:8080",
        fetch: fetchMock,
      }),
    );

    await expect(
      api.login({ email: "user@example.com", password: "password123!" }),
    ).resolves.toEqual({
      accessToken: "access-token",
      member: {
        id: 1,
        email: "user@example.com",
        nickname: "테크뷰",
      },
    });
  });

  it("signs up without returning response data", async () => {
    const fetchMock = vi.fn(async (request: Request) => {
      expect(request.url).toBe("http://localhost:8080/api/v1/auth/signup");
      expect(await request.json()).toEqual({
        email: "user@example.com",
        password: "password123!",
        nickname: "테크뷰",
      });

      return Response.json(
        { code: 0, message: "회원가입이 완료되었습니다." },
        { status: 201 },
      );
    });
    const api = createAuthApi(
      createTechViewApiClient({
        baseUrl: "http://localhost:8080",
        fetch: fetchMock,
      }),
    );

    await expect(
      api.signUp({
        email: "user@example.com",
        password: "password123!",
        nickname: "테크뷰",
      }),
    ).resolves.toBeUndefined();
  });

  it("refreshes the access token and loads the current member", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        Response.json({
          code: 0,
          message: "요청에 성공했습니다.",
          data: { accessToken: "refreshed-token" },
        }),
      )
      .mockResolvedValueOnce(
        Response.json({
          code: 0,
          message: "요청에 성공했습니다.",
          data: {
            id: 1,
            email: "user@example.com",
            nickname: "테크뷰",
          },
        }),
      );
    const api = createAuthApi(
      createTechViewApiClient({
        baseUrl: "http://localhost:8080",
        fetch: fetchMock,
      }),
    );

    await expect(api.refresh()).resolves.toBe("refreshed-token");
    await expect(api.getMe()).resolves.toEqual({
      id: 1,
      email: "user@example.com",
      nickname: "테크뷰",
    });
  });
});
