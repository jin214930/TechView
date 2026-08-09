import { afterEach, describe, expect, it, vi } from "vitest";

import { createInterviewEventsApi } from "@/features/interview/api/interview-events-api";
import { clearAccessToken, setAccessToken } from "@/shared/api/access-token";

function createStream(...chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      controller.close();
    },
  });
}

describe("interviewEventsApi", () => {
  afterEach(() => {
    clearAccessToken();
    vi.useRealTimers();
  });

  it("authenticates the fetch stream and parses progress events", async () => {
    setAccessToken("access-token");
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        createStream(
          'event: connected\ndata: {"sessionId":10}\n\n',
          'event: progress-changed\ndata: {"sessionId":10}\n\n',
        ),
        { status: 200 },
      ),
    );
    const onEvent = vi.fn();
    const onError = vi.fn();
    const subscription = createInterviewEventsApi({
      baseUrl: "http://localhost:8080",
      fetchImpl: fetchMock,
    }).subscribe(10, { onEvent, onError });

    await vi.waitFor(() => expect(onEvent).toHaveBeenCalledTimes(2));

    const requestInit = fetchMock.mock.calls[0]?.[1];
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/interviews/10/events",
      expect.objectContaining({ credentials: "include" }),
    );
    expect(new Headers(requestInit?.headers).get("Authorization")).toBe(
      "Bearer access-token",
    );
    expect(onEvent).toHaveBeenNthCalledWith(2, {
      name: "progress-changed",
      sessionId: 10,
    });

    subscription.close();
  });

  it("reconnects after a stream closes until the subscription is closed", async () => {
    vi.useFakeTimers();
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(createStream(), { status: 200 }),
    );
    const onError = vi.fn();
    const subscription = createInterviewEventsApi({
      baseUrl: "http://localhost:8080",
      fetchImpl: fetchMock,
      reconnectDelayMs: 10,
    }).subscribe(10, { onEvent: vi.fn(), onError });

    await vi.waitFor(() => expect(onError).toHaveBeenCalledOnce());
    await vi.advanceTimersByTimeAsync(10);
    await vi.waitFor(() => {
      if (fetchMock.mock.calls.length >= 2) {
        subscription.close();
      }
      expect(fetchMock.mock.calls.length).toBeGreaterThanOrEqual(2);
    });
    subscription.close();
  });
});
