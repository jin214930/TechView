import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import Home from "@/app/page";
import type { AuthApi } from "@/features/auth/api/auth-api";
import { AuthProviderCore } from "@/features/auth/model/auth-context";

const member = {
  id: 1,
  email: "user@example.com",
  nickname: "테크뷰",
};

function createApi(overrides: Partial<AuthApi> = {}): AuthApi {
  return {
    login: vi.fn(),
    signUp: vi.fn(),
    refresh: vi.fn().mockRejectedValue(new Error("no session")),
    getMe: vi.fn().mockResolvedValue(member),
    logout: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
}

describe("Home", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders the heading, core features, and process sections for anonymous users", async () => {
    render(
      <AuthProviderCore api={createApi()}>
        <Home />
      </AuthProviderCore>,
    );

    expect(
      screen.getByRole("heading", { level: 1, name: "TechView" }),
    ).toBeTruthy();

    const button = await screen.findByRole("link", { name: "무료로 시작하기" });
    expect(button.getAttribute("href")).toBe("/signup");

    expect(screen.getByText("음성 기반 면접")).toBeTruthy();
    expect(screen.getByText("조건부 꼬리 질문")).toBeTruthy();
    expect(screen.getByText("종합 피드백")).toBeTruthy();
  });

  it("changes CTA button text to '면접 보러가기' when authenticated", async () => {
    const api = createApi({
      refresh: vi.fn().mockResolvedValue("access-token"),
    });

    render(
      <AuthProviderCore api={api}>
        <Home />
      </AuthProviderCore>,
    );

    const button = await screen.findByRole("link", { name: "면접 보러가기" });
    expect(button.getAttribute("href")).toBe("/");
  });
});

