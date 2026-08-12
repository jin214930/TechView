"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";

import {
  createAuthApi,
  type AuthApi,
} from "@/features/auth/api/auth-api";
import type {
  LoginFormInput,
  SignUpFormInput,
} from "@/features/auth/model/auth-schema";
import type {
  AuthMember,
  AuthStatus,
} from "@/features/auth/model/auth-types";
import { clearInterviewStart } from "@/features/interview/model/interview-session-storage";
import {
  clearAccessToken,
  setAccessToken,
} from "@/shared/api/access-token";
import { getApiClient } from "@/shared/api/client";

interface AuthContextValue {
  status: AuthStatus;
  member: AuthMember | null;
  login(input: LoginFormInput): Promise<void>;
  signUp(input: Omit<SignUpFormInput, "passwordConfirm">): Promise<void>;
  deleteAccount(): Promise<void>;
  logout(): Promise<void>;
}

interface AuthProviderProps {
  children: ReactNode;
}

interface AuthProviderCoreProps extends AuthProviderProps {
  api: AuthApi;
}

const AuthContext = createContext<AuthContextValue | null>(null);
const restoreRequests = new WeakMap<AuthApi, Promise<AuthMember | null>>();

export function AuthProvider({ children }: AuthProviderProps) {
  const apiRef = useRef<AuthApi | null>(null);
  const resolveApi = useCallback(() => {
    apiRef.current ??= createAuthApi(getApiClient());
    return apiRef.current;
  }, []);

  return (
    <AuthProviderState resolveApi={resolveApi}>{children}</AuthProviderState>
  );
}

export function AuthProviderCore({ api, children }: AuthProviderCoreProps) {
  const resolveApi = useCallback(() => api, [api]);
  return (
    <AuthProviderState resolveApi={resolveApi}>{children}</AuthProviderState>
  );
}

function AuthProviderState({
  children,
  resolveApi,
}: AuthProviderProps & { resolveApi: () => AuthApi }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [member, setMember] = useState<AuthMember | null>(null);

  useEffect(() => {
    let active = true;

    restoreSession(resolveApi()).then((restoredMember) => {
      if (!active) {
        return;
      }

      setMember(restoredMember);
      setStatus(restoredMember ? "authenticated" : "anonymous");
    });

    return () => {
      active = false;
    };
  }, [resolveApi]);

  const login = useCallback(
    async (input: LoginFormInput) => {
      const result = await resolveApi().login(input);
      setAccessToken(result.accessToken);
      setMember(result.member);
      setStatus("authenticated");
    },
    [resolveApi],
  );

  const signUp = useCallback(
    async (input: Omit<SignUpFormInput, "passwordConfirm">) => {
      await resolveApi().signUp(input);
    },
    [resolveApi],
  );

  const logout = useCallback(async () => {
    try {
      await resolveApi().logout();
    } finally {
      clearAccessToken();
      clearInterviewStart();
      setMember(null);
      setStatus("anonymous");
    }
  }, [resolveApi]);

  const deleteAccount = useCallback(async () => {
    await resolveApi().deleteMe();
    clearAccessToken();
    clearInterviewStart();
    setMember(null);
    setStatus("anonymous");
  }, [resolveApi]);

  const value = useMemo<AuthContextValue>(
    () => ({ status, member, login, signUp, deleteAccount, logout }),
    [status, member, login, signUp, deleteAccount, logout],
  );

  return <AuthContext value={value}>{children}</AuthContext>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}

function restoreSession(api: AuthApi): Promise<AuthMember | null> {
  const existingRequest = restoreRequests.get(api);
  if (existingRequest) {
    return existingRequest;
  }

  const request = (async () => {
    try {
      const accessToken = await api.refresh();
      setAccessToken(accessToken);
      return await api.getMe();
    } catch {
      clearAccessToken();
      return null;
    }
  })();

  restoreRequests.set(api, request);
  void request.finally(() => restoreRequests.delete(api));
  return request;
}
