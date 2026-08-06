import { z } from "zod";

const EMAIL_MAX_LENGTH = 100;
const PASSWORD_MIN_LENGTH = 8;
const PASSWORD_MAX_LENGTH = 64;
const NICKNAME_MIN_LENGTH = 2;
const NICKNAME_MAX_LENGTH = 20;

const emailSchema = z
  .string()
  .trim()
  .min(1, "이메일을 입력해 주세요.")
  .max(EMAIL_MAX_LENGTH, "이메일은 100자 이하로 입력해 주세요.")
  .email("올바른 이메일 형식으로 입력해 주세요.");

const passwordSchema = z
  .string()
  .min(PASSWORD_MIN_LENGTH, "비밀번호는 8자 이상 입력해 주세요.")
  .max(PASSWORD_MAX_LENGTH, "비밀번호는 64자 이하로 입력해 주세요.")
  .regex(
    /^[\x21-\x7e]+$/,
    "비밀번호는 공백 없이 영문, 숫자, 특수문자만 사용할 수 있습니다.",
  );

export const loginSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, "비밀번호를 입력해 주세요."),
});

export const signUpSchema = z
  .object({
    email: emailSchema,
    password: passwordSchema,
    passwordConfirm: z.string().min(1, "비밀번호 확인을 입력해 주세요."),
    nickname: z
      .string()
      .trim()
      .min(NICKNAME_MIN_LENGTH, "닉네임은 2자 이상 입력해 주세요.")
      .max(NICKNAME_MAX_LENGTH, "닉네임은 20자 이하로 입력해 주세요.")
      .regex(
        /^[가-힣a-zA-Z0-9_]+$/,
        "닉네임은 한글, 영문, 숫자, 밑줄만 사용할 수 있습니다.",
      ),
  })
  .refine((input) => input.password === input.passwordConfirm, {
    message: "비밀번호가 일치하지 않습니다.",
    path: ["passwordConfirm"],
  });

export type LoginFormInput = z.infer<typeof loginSchema>;
export type SignUpFormInput = z.infer<typeof signUpSchema>;
