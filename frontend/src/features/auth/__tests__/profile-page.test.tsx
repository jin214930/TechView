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
import { ProfilePage } from "@/features/auth/ui/profile-page";

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
    refresh: vi.fn().mockResolvedValue("access-token"),
    getMe: vi.fn().mockResolvedValue(member),
    deleteMe: vi.fn().mockResolvedValue(undefined),
    logout: vi.fn(),
    ...overrides,
  };
}

function renderProfile(api: AuthApi = createApi()) {
  return render(
    <AuthProviderCore api={api}>
      <ProfilePage />
    </AuthProviderCore>,
  );
}

describe("ProfilePage", () => {
  afterEach(() => {
    cleanup();
    replaceMock.mockReset();
  });

  it("shows the authenticated member information", async () => {
    renderProfile();

    expect(await screen.findByRole("heading", { name: "내 프로필" })).toBeTruthy();
    expect(screen.getByText("user@example.com")).toBeTruthy();
    expect(screen.getByText("테크뷰")).toBeTruthy();
  });

  it("redirects anonymous users to login", async () => {
    renderProfile(
      createApi({ refresh: vi.fn().mockRejectedValue(new Error("no session")) }),
    );

    await waitFor(() =>
      expect(replaceMock).toHaveBeenCalledWith(
        "/login?next=%2Fprofile",
      ),
    );
  });

  it("requires confirmation before deleting the account", async () => {
    const api = createApi();
    renderProfile(api);

    await screen.findByRole("heading", { name: "내 프로필" });
    fireEvent.click(screen.getByRole("button", { name: "회원 탈퇴" }));

    expect(screen.getByRole("dialog")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "취소" }));
    expect(api.deleteMe).not.toHaveBeenCalled();
  });

  it("deletes the account after confirmation and returns home", async () => {
    const api = createApi();
    renderProfile(api);

    await screen.findByRole("heading", { name: "내 프로필" });
    fireEvent.click(screen.getByRole("button", { name: "회원 탈퇴" }));
    fireEvent.click(screen.getByRole("button", { name: "탈퇴하기" }));

    await waitFor(() => expect(api.deleteMe).toHaveBeenCalledOnce());
    expect(replaceMock).toHaveBeenCalledWith("/");
  });

  it("shows an error and keeps the confirmation open when deletion fails", async () => {
    const api = createApi({
      deleteMe: vi.fn().mockRejectedValue(new Error("delete failed")),
    });
    renderProfile(api);

    await screen.findByRole("heading", { name: "내 프로필" });
    fireEvent.click(screen.getByRole("button", { name: "회원 탈퇴" }));
    fireEvent.click(screen.getByRole("button", { name: "탈퇴하기" }));

    await waitFor(() => expect(screen.getByRole("alert")).toBeTruthy());
    expect(screen.getByRole("dialog")).toBeTruthy();
    expect(replaceMock).not.toHaveBeenCalledWith("/");
  });
});
