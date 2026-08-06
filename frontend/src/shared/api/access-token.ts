let accessToken: string | undefined;

export function getAccessToken(): string | undefined {
  if (typeof window === "undefined") {
    return undefined;
  }

  return accessToken;
}

export function setAccessToken(token: string): void {
  if (typeof window === "undefined") {
    throw new Error("Access token can only be stored in the browser");
  }

  if (!token.trim()) {
    throw new Error("Access token must not be blank");
  }

  accessToken = token;
}

export function clearAccessToken(): void {
  accessToken = undefined;
}
