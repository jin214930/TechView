export interface AuthMember {
  id: number;
  email: string;
  nickname: string;
}

export interface LoginResult {
  accessToken: string;
  member: AuthMember;
}

export type AuthStatus = "loading" | "authenticated" | "anonymous";
