import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { AuthApi } from "@/features/auth/api/auth-api";
import { AuthProviderCore } from "@/features/auth/model/auth-context";
import { SiteHeader } from "@/widgets/site-header";

const { replaceMock } = vi.hoisted(() => ({ replaceMock: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock }),
}));

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
    deleteMe: vi.fn().mockResolvedValue(undefined),
    logout: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
}

describe("SiteHeader", () => {
  afterEach(() => {
    cleanup();
    replaceMock.mockReset();
  });

  it("shows login and sign-up links to anonymous users", async () => {
    render(
      <AuthProviderCore api={createApi()}>
        <SiteHeader />
      </AuthProviderCore>,
    );

    expect(await screen.findByRole("link", { name: "로그인" })).toBeTruthy();
    expect(screen.getByRole("link", { name: "회원가입" })).toBeTruthy();
  });

  it("shows the member and logs out an authenticated user", async () => {
    const api = createApi({
      refresh: vi.fn().mockResolvedValue("access-token"),
    });
    render(
      <AuthProviderCore api={api}>
        <SiteHeader />
      </AuthProviderCore>,
    );

    expect(await screen.findByText("테크뷰님")).toBeTruthy();
    expect(screen.getByRole("link", { name: "프로필" }).getAttribute("href")).toBe(
      "/profile",
    );
    fireEvent.click(screen.getByRole("button", { name: "로그아웃" }));

    await waitFor(() => expect(api.logout).toHaveBeenCalledOnce());
    expect(replaceMock).toHaveBeenCalledWith("/");
  });
});
