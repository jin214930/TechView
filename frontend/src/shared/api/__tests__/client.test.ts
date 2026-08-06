import { afterEach, describe, expect, it, vi } from "vitest";

import { createTechViewApiClient } from "@/shared/api/client";

describe("createTechViewApiClient", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("uses the configured base URL and includes cookies", async () => {
    const fetchMock = vi.fn(async (request: Request) => {
      expect(request.url).toBe("http://localhost:8080/api/v1/auth/login");
      expect(request.credentials).toBe("include");

      return Response.json({
        code: 0,
        message: "로그인이 완료되었습니다.",
        data: {
          accessToken: "access-token",
          memberId: 1,
          email: "user@example.com",
          nickname: "user",
        },
      });
    });
    const client = createTechViewApiClient({
      baseUrl: "http://localhost:8080/",
      fetch: fetchMock,
    });

    const result = await client.POST("/api/v1/auth/login", {
      body: {
        email: "user@example.com",
        password: "password123!",
      },
    });

    expect(result.data?.data?.email).toBe("user@example.com");
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("rejects a missing API base URL", () => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "");

    expect(() => createTechViewApiClient()).toThrow(
      "NEXT_PUBLIC_API_BASE_URL is required",
    );
  });

  it("rejects a non-HTTP API base URL", () => {
    expect(() =>
      createTechViewApiClient({ baseUrl: "ftp://localhost:8080" }),
    ).toThrow("HTTP or HTTPS");
  });
});
