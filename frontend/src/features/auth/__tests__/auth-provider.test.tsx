import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { AuthApi } from "@/features/auth/api/auth-api";
import {
  AuthProviderCore,
  useAuth,
} from "@/features/auth/model/auth-context";
import {
  clearAccessToken,
  getAccessToken,
} from "@/shared/api/access-token";

const member = {
  id: 1,
  email: "user@example.com",
  nickname: "테크뷰",
};

function createApi(overrides: Partial<AuthApi> = {}): AuthApi {
  return {
    login: vi.fn().mockResolvedValue({
      accessToken: "login-token",
      member,
    }),
    signUp: vi.fn().mockResolvedValue(undefined),
    refresh: vi.fn().mockRejectedValue(new Error("no session")),
    getMe: vi.fn().mockResolvedValue(member),
    logout: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
}

function AuthProbe() {
  const auth = useAuth();

  return (
    <div>
      <p data-testid="status">{auth.status}</p>
      <p data-testid="member">{auth.member?.nickname ?? "none"}</p>
      <button
        type="button"
        onClick={() =>
          auth.login({
            email: "user@example.com",
            password: "password123!",
          })
        }
      >
        login
      </button>
      <button type="button" onClick={() => auth.logout()}>
        logout
      </button>
    </div>
  );
}

describe("AuthProvider", () => {
  afterEach(() => {
    cleanup();
    clearAccessToken();
  });

  it("restores a session with the refresh cookie on mount", async () => {
    const api = createApi({
      refresh: vi.fn().mockResolvedValue("refreshed-token"),
    });

    render(
      <AuthProviderCore api={api}>
        <AuthProbe />
      </AuthProviderCore>,
    );

    expect(screen.getByTestId("status").textContent).toBe("loading");
    await waitFor(() =>
      expect(screen.getByTestId("status").textContent).toBe("authenticated"),
    );
    expect(screen.getByTestId("member").textContent).toBe("테크뷰");
    expect(getAccessToken()).toBe("refreshed-token");
  });

  it("becomes anonymous when no refresh session exists", async () => {
    render(
      <AuthProviderCore api={createApi()}>
        <AuthProbe />
      </AuthProviderCore>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("status").textContent).toBe("anonymous"),
    );
    expect(getAccessToken()).toBeUndefined();
  });

  it("stores login state and clears it on logout", async () => {
    const api = createApi();
    render(
      <AuthProviderCore api={api}>
        <AuthProbe />
      </AuthProviderCore>,
    );
    await waitFor(() =>
      expect(screen.getByTestId("status").textContent).toBe("anonymous"),
    );

    fireEvent.click(screen.getByRole("button", { name: "login" }));

    await waitFor(() =>
      expect(screen.getByTestId("status").textContent).toBe("authenticated"),
    );
    expect(getAccessToken()).toBe("login-token");

    window.sessionStorage.setItem("techview.interview.start", "stale-handoff");

    fireEvent.click(screen.getByRole("button", { name: "logout" }));

    await waitFor(() =>
      expect(screen.getByTestId("status").textContent).toBe("anonymous"),
    );
    expect(getAccessToken()).toBeUndefined();
    expect(window.sessionStorage.getItem("techview.interview.start")).toBeNull();
  });
});
