import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { AuthApi } from "@/features/auth/api/auth-api";
import { AuthProviderCore } from "@/features/auth/model/auth-context";
import type { InterviewApi } from "@/features/interview/api/interview-api";
import {
  AudioEnvironmentError,
} from "@/features/interview/model/audio-environment";
import { InterviewEnvironmentSetup } from "@/features/interview/ui/interview-environment-setup";

const { replaceMock, pushMock, startMicrophoneEnvironmentTestMock } = vi.hoisted(
  () => ({
    replaceMock: vi.fn(),
    pushMock: vi.fn(),
    startMicrophoneEnvironmentTestMock: vi.fn(),
  }),
);

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock, push: pushMock }),
}));

vi.mock("@/features/interview/model/audio-environment", async () => {
  const actual = await vi.importActual<
    typeof import("@/features/interview/model/audio-environment")
  >("@/features/interview/model/audio-environment");
  return {
    ...actual,
    startMicrophoneEnvironmentTest: startMicrophoneEnvironmentTestMock,
  };
});

const member = {
  id: 1,
  email: "user@example.com",
  nickname: "테스트 사용자",
};

function createAuthApi(overrides: Partial<AuthApi> = {}): AuthApi {
  return {
    login: vi.fn(),
    signUp: vi.fn(),
    refresh: vi.fn().mockResolvedValue("access-token"),
    getMe: vi.fn().mockResolvedValue(member),
    logout: vi.fn(),
    ...overrides,
  };
}

function createInterviewApi(
  overrides: Partial<InterviewApi> = {},
): InterviewApi {
  return {
    getActive: vi.fn(),
    startOrResume: vi.fn().mockResolvedValue({
      resumed: true,
      sessionId: 10,
      turnId: 21,
      turnOrder: 3,
      turnType: "CORE",
      question: "Spring 설명을 해주세요.",
    }),
    getProgress: vi.fn(),
    ...overrides,
  };
}

function renderSetup(
  api: InterviewApi,
  authApi: AuthApi = createAuthApi(),
) {
  return render(
    <AuthProviderCore api={authApi}>
      <InterviewEnvironmentSetup api={api} />
    </AuthProviderCore>,
  );
}

function createPendingMicrophoneTestSession() {
  let resolveResult!: (result: {
    blob: Blob;
    durationMs: number;
  }) => void;
  const result = new Promise<{ blob: Blob; durationMs: number }>((resolve) => {
    resolveResult = resolve;
  });

  return {
    session: {
      result,
      stop: vi.fn(() => result),
      cancel: vi.fn(),
      retainStream: vi.fn(),
    },
    resolveResult,
  };
}

describe("InterviewEnvironmentSetup", () => {
  afterEach(() => {
    cleanup();
    window.sessionStorage.clear();
    replaceMock.mockReset();
    pushMock.mockReset();
    startMicrophoneEnvironmentTestMock.mockReset();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("redirects anonymous users to login", async () => {
    const authApi = createAuthApi({
      refresh: vi.fn().mockRejectedValue(new Error("no session")),
    });

    renderSetup(createInterviewApi(), authApi);

    await waitFor(() =>
      expect(replaceMock).toHaveBeenCalledWith(
        "/login?next=%2Finterviews%2Fsetup",
      ),
    );
    expect(screen.getByText("로그인 페이지로 이동하고 있습니다.")).toBeTruthy();
  });

  it("requires playback confirmation before starting or resuming", async () => {
    const api = createInterviewApi();
    const { session: recordingSession, resolveResult } =
      createPendingMicrophoneTestSession();
    startMicrophoneEnvironmentTestMock.mockResolvedValue(recordingSession);
    vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:test-audio");

    renderSetup(api);

    const startButton = await screen.findByRole("button", {
      name: "면접 시작하기",
    });
    expect((startButton as HTMLButtonElement).disabled).toBe(true);

    fireEvent.click(screen.getByRole("button", { name: "마이크 테스트 시작" }));
    expect(
      await screen.findByText("아래 문장을 읽은 뒤 녹음 끝내기를 눌러 주세요."),
    ).toBeTruthy();
    expect(screen.getByText("음성 확인 문장")).toBeTruthy();
    expect(screen.getByText("00:00")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "녹음 끝내기" }));
    expect(recordingSession.stop).toHaveBeenCalledOnce();
    resolveResult({
      blob: new Blob(["test audio"], { type: "audio/webm" }),
      durationMs: 1000,
    });
    expect(
      await screen.findByText("녹음된 음성을 재생하고 소리를 확인해 주세요."),
    ).toBeTruthy();
    expect((startButton as HTMLButtonElement).disabled).toBe(true);

    const audio = screen.getByLabelText("음성 환경 테스트 녹음");
    fireEvent.canPlay(audio);
    const confirmation = screen.getByRole("checkbox", {
      name: "소리가 잘 들려요.",
    });
    expect((confirmation as HTMLInputElement).disabled).toBe(false);
    expect((startButton as HTMLButtonElement).disabled).toBe(true);

    fireEvent.click(confirmation);
    expect(
      await screen.findByText("음성 환경 테스트가 완료되었습니다."),
    ).toBeTruthy();
    expect((startButton as HTMLButtonElement).disabled).toBe(false);

    fireEvent.click(startButton);

    await waitFor(() => expect(api.startOrResume).toHaveBeenCalledOnce());
    expect(pushMock).toHaveBeenCalledWith("/interviews/10");
    expect(window.sessionStorage.getItem("techview.interview.start")).toContain(
      '"sessionId":10',
    );
  });

  it("blocks the interview when microphone permission is denied", async () => {
    const api = createInterviewApi();
    startMicrophoneEnvironmentTestMock.mockRejectedValue(
      new AudioEnvironmentError(
        "마이크 권한을 허용해야 면접을 진행할 수 있습니다.",
      ),
    );

    renderSetup(api);

    fireEvent.click(await screen.findByRole("button", { name: "마이크 테스트 시작" }));

    expect(
      await screen.findByText("마이크 권한을 허용해야 면접을 진행할 수 있습니다."),
    ).toBeTruthy();
    expect(
      (screen.getByRole("button", { name: "면접 시작하기" }) as HTMLButtonElement)
        .disabled,
    ).toBe(true);
    expect(api.startOrResume).not.toHaveBeenCalled();
  });

  it("can cancel the initial microphone permission request and retry", async () => {
    let resolveSession!: (session: { cancel: () => void }) => void;
    startMicrophoneEnvironmentTestMock.mockReturnValue(
      new Promise((resolve) => {
        resolveSession = resolve;
      }),
    );

    renderSetup(createInterviewApi());

    fireEvent.click(
      await screen.findByRole("button", { name: "마이크 테스트 시작" }),
    );
    expect(
      screen.getByRole("button", { name: "마이크 테스트 취소" }),
    ).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "마이크 테스트 취소" }));

    expect(
      await screen.findByRole("button", { name: "마이크 테스트 시작" }),
    ).toBeTruthy();
    expect(screen.getByRole("alert").textContent).toContain(
      "다시 시도해 주세요.",
    );

    const cancel = vi.fn();
    resolveSession({ cancel });
    await waitFor(() => expect(cancel).toHaveBeenCalledOnce());
  });

  it("shows elapsed recording time while the microphone test is active", async () => {
    const { session, resolveResult } = createPendingMicrophoneTestSession();
    startMicrophoneEnvironmentTestMock.mockResolvedValue(session);

    renderSetup(createInterviewApi());
    const startButton = await screen.findByRole("button", {
      name: "마이크 테스트 시작",
    });
    vi.useFakeTimers();

    fireEvent.click(startButton);
    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.getByText("00:00")).toBeTruthy();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
    expect(screen.getByText("00:01")).toBeTruthy();

    await act(async () => {
      resolveResult({
        blob: new Blob(["test audio"], { type: "audio/webm" }),
        durationMs: 1000,
      });
      await Promise.resolve();
    });
    expect(screen.getByText("녹음된 음성을 재생하고 소리를 확인해 주세요.")).toBeTruthy();
  });
});
