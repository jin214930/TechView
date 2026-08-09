import { ApiClientError } from "@/shared/api/response";
import { AudioEnvironmentError } from "@/features/interview/model/audio-environment";

export function getInterviewErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError) {
    return error.message;
  }

  if (error instanceof AudioEnvironmentError) {
    return error.message;
  }

  return "면접 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
}
