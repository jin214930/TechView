import type { Middleware } from "openapi-fetch";

import { getAccessToken } from "@/shared/api/access-token";

export function createAuthorizationMiddleware(): Middleware {
  return {
    onRequest({ request }) {
      const accessToken = getAccessToken();

      if (!accessToken || request.headers.has("Authorization")) {
        return request;
      }

      request.headers.set("Authorization", `Bearer ${accessToken}`);
      return request;
    },
  };
}
