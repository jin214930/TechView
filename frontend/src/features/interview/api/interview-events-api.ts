import { getAccessToken } from "@/shared/api/access-token";

export type InterviewEventName =
  | "connected"
  | "progress-changed"
  | "feedback-status-changed";

export interface InterviewEvent {
  name: InterviewEventName;
  sessionId: number;
}

export interface InterviewEventsSubscription {
  close(): void;
}

export interface InterviewEventsApi {
  subscribe(
    sessionId: number,
    handlers: {
      onEvent(event: InterviewEvent): void;
      onError(error: unknown): void;
    },
  ): InterviewEventsSubscription;
}

interface InterviewEventsApiOptions {
  baseUrl: string;
  fetchImpl?: typeof fetch;
  getToken?: () => string | undefined;
  reconnectDelayMs?: number;
}

const DEFAULT_RECONNECT_DELAY_MS = 3_000;

export function createInterviewEventsApi(
  options: InterviewEventsApiOptions,
): InterviewEventsApi {
  const fetchImpl = options.fetchImpl ?? fetch;
  const getToken = options.getToken ?? getAccessToken;
  const reconnectDelayMs = options.reconnectDelayMs ?? DEFAULT_RECONNECT_DELAY_MS;
  const baseUrl = normalizeBaseUrl(options.baseUrl);

  return {
    subscribe(sessionId, handlers) {
      let closed = false;
      let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
      let abortController: AbortController | undefined;

      const scheduleReconnect = () => {
        if (closed || reconnectTimer !== undefined) {
          return;
        }

        reconnectTimer = setTimeout(() => {
          reconnectTimer = undefined;
          void connect();
        }, reconnectDelayMs);
      };

      const connect = async (): Promise<void> => {
        if (closed) {
          return;
        }

        abortController = new AbortController();
        const headers = new Headers({ Accept: "text/event-stream" });
        const token = getToken();
        if (token) {
          headers.set("Authorization", `Bearer ${token}`);
        }

        try {
          const response = await fetchImpl(
            `${baseUrl}/api/v1/interviews/${sessionId}/events`,
            {
              method: "GET",
              headers,
              credentials: "include",
              signal: abortController.signal,
            },
          );

          if (!response.ok) {
            throw new Error(`Interview event stream failed with ${response.status}`);
          }
          if (!response.body) {
            throw new Error("Interview event stream has no response body");
          }

          await consumeEventStream(response.body, (event) => {
            if (!closed) {
              handlers.onEvent(event);
            }
          });

          if (!closed) {
            const error = new Error("Interview event stream was closed");
            handlers.onError(error);
            scheduleReconnect();
          }
        } catch (error) {
          if (closed || isAbortError(error)) {
            return;
          }

          handlers.onError(error);
          scheduleReconnect();
        }
      };

      void connect();

      return {
        close() {
          closed = true;
          if (reconnectTimer !== undefined) {
            clearTimeout(reconnectTimer);
          }
          abortController?.abort();
        },
      };
    },
  };
}

async function consumeEventStream(
  body: ReadableStream<Uint8Array>,
  onEvent: (event: InterviewEvent) => void,
): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    buffer += decoder.decode(value, { stream: !done });

    const blocks = buffer.split(/\r?\n\r?\n/);
    buffer = blocks.pop() ?? "";
    blocks.forEach((block) => {
      const event = parseEventBlock(block);
      if (event) {
        onEvent(event);
      }
    });

    if (done) {
      const event = parseEventBlock(buffer);
      if (event) {
        onEvent(event);
      }
      return;
    }
  }
}

function parseEventBlock(block: string): InterviewEvent | null {
  let name: string | undefined;
  const dataLines: string[] = [];

  block.split(/\r?\n/).forEach((line) => {
    if (line.startsWith("event:")) {
      name = line.slice("event:".length).trim();
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice("data:".length).trimStart());
    }
  });

  if (!name || dataLines.length === 0) {
    return null;
  }

  if (
    name !== "connected" &&
    name !== "progress-changed" &&
    name !== "feedback-status-changed"
  ) {
    return null;
  }

  const data: unknown = JSON.parse(dataLines.join("\n"));
  if (!isSessionPayload(data)) {
    throw new Error("Interview event payload has an invalid sessionId");
  }

  return { name, sessionId: data.sessionId };
}

function isSessionPayload(value: unknown): value is { sessionId: number } {
  return (
    typeof value === "object" &&
    value !== null &&
    "sessionId" in value &&
    typeof value.sessionId === "number" &&
    Number.isSafeInteger(value.sessionId) &&
    value.sessionId > 0
  );
}

function normalizeBaseUrl(value: string): string {
  const url = new URL(value);
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error("Interview event base URL must use HTTP or HTTPS");
  }
  return url.toString().replace(/\/$/, "");
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}
