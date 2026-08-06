import { afterEach, describe, expect, it, vi } from "vitest";

import {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
} from "@/shared/api/access-token";

describe("access token memory store", () => {
  afterEach(() => {
    clearAccessToken();
    vi.unstubAllGlobals();
  });

  it("keeps the access token only in module memory", () => {
    setAccessToken("access-token");

    expect(getAccessToken()).toBe("access-token");
    expect(localStorage.getItem("accessToken")).toBeNull();
    expect(sessionStorage.getItem("accessToken")).toBeNull();
  });

  it("clears the stored access token", () => {
    setAccessToken("access-token");

    clearAccessToken();

    expect(getAccessToken()).toBeUndefined();
  });

  it("rejects a blank access token", () => {
    expect(() => setAccessToken("   ")).toThrow("Access token must not be blank");
  });

  it("does not store an access token in the server environment", () => {
    vi.stubGlobal("window", undefined);

    expect(getAccessToken()).toBeUndefined();
    expect(() => setAccessToken("access-token")).toThrow(
      "Access token can only be stored in the browser",
    );
  });
});
