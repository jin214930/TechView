import { ApiClientError } from "@/shared/api/response";

export function getAuthErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError) {
    return error.message;
  }

  return "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
}
