import type { components } from "@/shared/api/schema";
import type { TechViewApiClient } from "@/shared/api/client";
import { unwrapApiResponse } from "@/shared/api/response";
import type {
  AuthMember,
  LoginResult,
} from "@/features/auth/model/auth-types";

type LoginRequest = components["schemas"]["LoginRequest"];
type SignUpRequest = components["schemas"]["SignUpRequest"];

export interface AuthApi {
  login(request: LoginRequest): Promise<LoginResult>;
  signUp(request: SignUpRequest): Promise<void>;
  refresh(): Promise<string>;
  getMe(): Promise<AuthMember>;
  logout(): Promise<void>;
}

export function createAuthApi(client: TechViewApiClient): AuthApi {
  return {
    async login(request) {
      const result = await client.POST("/api/v1/auth/login", { body: request });
      const data = unwrapApiResponse(result);

      return {
        accessToken: requireString(data?.accessToken, "accessToken"),
        member: {
          id: requireNumber(data?.memberId, "memberId"),
          email: requireString(data?.email, "email"),
          nickname: requireString(data?.nickname, "nickname"),
        },
      };
    },

    async signUp(request) {
      const result = await client.POST("/api/v1/auth/signup", { body: request });
      unwrapApiResponse(result);
    },

    async refresh() {
      const result = await client.POST("/api/v1/auth/refresh");
      const data = unwrapApiResponse(result);
      return requireString(data?.accessToken, "accessToken");
    },

    async getMe() {
      const result = await client.GET("/api/v1/members/me");
      const data = unwrapApiResponse(result);

      return {
        id: requireNumber(data?.id, "id"),
        email: requireString(data?.email, "email"),
        nickname: requireString(data?.nickname, "nickname"),
      };
    },

    async logout() {
      const result = await client.POST("/api/v1/auth/logout");
      unwrapApiResponse(result);
    },
  };
}

function requireString(value: string | undefined, field: string): string {
  if (!value) {
    throw new Error(`API response is missing ${field}`);
  }
  return value;
}

function requireNumber(value: number | undefined, field: string): number {
  if (value === undefined) {
    throw new Error(`API response is missing ${field}`);
  }
  return value;
}
