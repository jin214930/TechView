import { describe, expect, it } from "vitest";

import {
  loginSchema,
  signUpSchema,
} from "@/features/auth/model/auth-schema";

describe("loginSchema", () => {
  it("accepts valid login credentials", () => {
    expect(
      loginSchema.safeParse({
        email: "user@example.com",
        password: "password123!",
      }).success,
    ).toBe(true);
  });

  it("rejects an invalid email", () => {
    const result = loginSchema.safeParse({
      email: "invalid-email",
      password: "password123!",
    });

    expect(result.success).toBe(false);
  });
});

describe("signUpSchema", () => {
  const validInput = {
    email: "user@example.com",
    password: "password123!",
    passwordConfirm: "password123!",
    nickname: "테크뷰_01",
  };

  it("accepts valid sign-up input", () => {
    expect(signUpSchema.safeParse(validInput).success).toBe(true);
  });

  it("requires matching passwords", () => {
    const result = signUpSchema.safeParse({
      ...validInput,
      passwordConfirm: "different123!",
    });

    expect(result.success).toBe(false);
  });

  it("rejects whitespace and non-ASCII password characters", () => {
    expect(
      signUpSchema.safeParse({ ...validInput, password: "password 123!" })
        .success,
    ).toBe(false);
    expect(
      signUpSchema.safeParse({
        ...validInput,
        password: "비밀번호123!",
        passwordConfirm: "비밀번호123!",
      }).success,
    ).toBe(false);
  });

  it("accepts only Korean, English, digits, and underscores in nicknames", () => {
    expect(
      signUpSchema.safeParse({ ...validInput, nickname: "invalid name" })
        .success,
    ).toBe(false);
  });
});
