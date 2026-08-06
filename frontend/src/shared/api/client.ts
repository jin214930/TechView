import createClient, {
  type Client,
  type ClientOptions,
} from "openapi-fetch";

import type { paths } from "@/shared/api/schema";

export type TechViewApiClient = Client<paths>;

let sharedClient: TechViewApiClient | undefined;

export function createTechViewApiClient(
  options: ClientOptions = {},
): TechViewApiClient {
  const baseUrl = normalizeApiBaseUrl(
    options.baseUrl ?? process.env.NEXT_PUBLIC_API_BASE_URL,
  );

  return createClient<paths>({
    ...options,
    baseUrl,
    credentials: "include",
  });
}

export function getApiClient(): TechViewApiClient {
  sharedClient ??= createTechViewApiClient();
  return sharedClient;
}

function normalizeApiBaseUrl(value: string | undefined): string {
  if (!value) {
    throw new Error("NEXT_PUBLIC_API_BASE_URL is required");
  }

  const url = new URL(value);

  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error("NEXT_PUBLIC_API_BASE_URL must use HTTP or HTTPS");
  }

  return url.toString().replace(/\/$/, "");
}
