import { describe, expect, it } from "vitest";

import { getSafeNextPath } from "@/features/auth/model/safe-next-path";

describe("getSafeNextPath", () => {
  it("keeps an internal application path", () => {
    expect(getSafeNextPath("/interviews/prepare?resume=true")).toBe(
      "/interviews/prepare?resume=true",
    );
  });

  it("rejects external and executable URLs", () => {
    expect(getSafeNextPath("https://evil.example/path")).toBe("/");
    expect(getSafeNextPath("//evil.example/path")).toBe("/");
    expect(getSafeNextPath("javascript:alert(1)")).toBe("/");
    expect(getSafeNextPath("/\\evil.example/path")).toBe("/");
  });

  it("uses the first value and falls back to home", () => {
    expect(getSafeNextPath(["/interviews", "/profile"])).toBe("/interviews");
    expect(getSafeNextPath(undefined)).toBe("/");
  });
});
