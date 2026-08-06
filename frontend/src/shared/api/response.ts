export interface ApiResponseBody<T = unknown> {
  code?: unknown;
  message?: unknown;
  data?: T;
}

export interface ApiFetchResult<T> {
  data?: ApiResponseBody<T>;
  error?: ApiResponseBody;
  response: Response;
}

export class ApiClientError extends Error {
  readonly code: number;
  readonly status: number;

  constructor(code: number, status: number, message: string) {
    super(message);
    this.name = "ApiClientError";
    this.code = code;
    this.status = status;
  }
}

export function unwrapApiResponse<T>(result: ApiFetchResult<T>): T | undefined {
  const body = result.error ?? result.data;

  if (!body) {
    throw new ApiClientError(
      -1,
      result.response.status,
      "API 응답 본문이 없습니다.",
    );
  }

  const code = typeof body.code === "number" ? body.code : -1;
  const message =
    typeof body.message === "string" && body.message.length > 0
      ? body.message
      : "요청을 처리할 수 없습니다.";

  if (result.error || !result.response.ok || code !== 0) {
    throw new ApiClientError(code, result.response.status, message);
  }

  return result.data?.data;
}
