export type InterviewTurnType = "CORE" | "FOLLOW_UP";

export type InterviewProgressStatus =
  | "PROCESSING"
  | "TURN_READY"
  | "COMPLETED"
  | "FAILED";

export interface ActiveInterview {
  hasActiveSession: boolean;
  sessionId: number | null;
  startedAt: string | null;
  currentTurnOrder: number | null;
}

export interface InterviewStart {
  resumed: boolean;
  sessionId: number;
  turnId: number;
  turnOrder: number;
  turnType: InterviewTurnType;
  question: string;
}

export interface InterviewProgress {
  sessionId: number;
  status: InterviewProgressStatus;
  turnId: number | null;
  turnOrder: number | null;
  turnType: InterviewTurnType | null;
  question: string | null;
  errorCode: string | null;
  errorMessage: string | null;
}
