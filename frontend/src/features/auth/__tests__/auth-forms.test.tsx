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
import { LoginForm } from "@/features/auth/ui/login-form";
import { SignUpForm } from "@/features/auth/ui/signup-form";
import { ApiClientError } from "@/shared/api/response";

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
    login: vi.fn().mockResolvedValue({ accessToken: "access-token", member }),
    signUp: vi.fn().mockResolvedValue(undefined),
    refresh: vi.fn().mockRejectedValue(new Error("no session")),
    getMe: vi.fn().mockResolvedValue(member),
    logout: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
}

function renderWithAuth(ui: React.ReactNode, api: AuthApi) {
  return render(<AuthProviderCore api={api}>{ui}</AuthProviderCore>);
}

async function waitForEnabledSubmit(name: "로그인" | "회원가입") {
  await waitFor(() =>
    expect(
      (screen.getByRole("button", { name }) as HTMLButtonElement).disabled,
    ).toBe(false),
  );
}

describe("LoginForm", () => {
  afterEach(() => {
    cleanup();
    replaceMock.mockReset();
  });

  it("logs in and returns to the safe destination", async () => {
    const api = createApi();
    renderWithAuth(
      <LoginForm nextPath="/interviews/prepare" signUpSucceeded={false} />,
      api,
    );
    await waitForEnabledSubmit("로그인");

    fireEvent.change(screen.getByLabelText("이메일"), {
      target: { value: "user@example.com" },
    });
    fireEvent.change(screen.getByLabelText("비밀번호"), {
      target: { value: "password123!" },
    });
    fireEvent.click(screen.getByRole("button", { name: "로그인" }));

    await waitFor(() => expect(api.login).toHaveBeenCalledOnce());
    expect(replaceMock).toHaveBeenCalledWith("/interviews/prepare");
  });

  it("shows the backend login error", async () => {
    const api = createApi({
      login: vi
        .fn()
        .mockRejectedValue(
          new ApiClientError(2001, 401, "이메일 또는 비밀번호가 일치하지 않습니다."),
        ),
    });
    renderWithAuth(
      <LoginForm nextPath="/" signUpSucceeded={false} />,
      api,
    );
    await waitForEnabledSubmit("로그인");

    fireEvent.change(screen.getByLabelText("이메일"), {
      target: { value: "user@example.com" },
    });
    fireEvent.change(screen.getByLabelText("비밀번호"), {
      target: { value: "wrong-password" },
    });
    fireEvent.click(screen.getByRole("button", { name: "로그인" }));

    expect(
      await screen.findByRole("alert", {
        name: "이메일 또는 비밀번호가 일치하지 않습니다.",
      }),
    ).toBeTruthy();
  });

  it("announces a completed sign-up", () => {
    renderWithAuth(
      <LoginForm nextPath="/" signUpSucceeded />,
      createApi(),
    );

    expect(screen.getByText(/회원가입이 완료되었습니다/)).toBeTruthy();
  });
});

describe("SignUpForm", () => {
  afterEach(() => {
    cleanup();
    replaceMock.mockReset();
  });

  it("signs up and moves to login", async () => {
    const api = createApi();
    renderWithAuth(<SignUpForm nextPath="/" />, api);
    await waitForEnabledSubmit("회원가입");

    fireEvent.change(screen.getByLabelText("이메일"), {
      target: { value: "user@example.com" },
    });
    fireEvent.change(screen.getByLabelText("닉네임"), {
      target: { value: "테크뷰" },
    });
    fireEvent.change(screen.getByLabelText("비밀번호"), {
      target: { value: "password123!" },
    });
    fireEvent.change(screen.getByLabelText("비밀번호 확인"), {
      target: { value: "password123!" },
    });
    fireEvent.click(screen.getByRole("button", { name: "회원가입" }));

    await waitFor(() => expect(api.signUp).toHaveBeenCalledOnce());
    expect(replaceMock).toHaveBeenCalledWith("/login?signup=success");
  });

  it("shows a password mismatch error", async () => {
    const api = createApi();
    renderWithAuth(<SignUpForm nextPath="/" />, api);
    await waitForEnabledSubmit("회원가입");

    fireEvent.change(screen.getByLabelText("이메일"), {
      target: { value: "user@example.com" },
    });
    fireEvent.change(screen.getByLabelText("닉네임"), {
      target: { value: "테크뷰" },
    });
    fireEvent.change(screen.getByLabelText("비밀번호"), {
      target: { value: "password123!" },
    });
    fireEvent.change(screen.getByLabelText("비밀번호 확인"), {
      target: { value: "different123!" },
    });
    fireEvent.click(screen.getByRole("button", { name: "회원가입" }));

    expect(await screen.findByText("비밀번호가 일치하지 않습니다.")).toBeTruthy();
    expect(api.signUp).not.toHaveBeenCalled();
  });
});
