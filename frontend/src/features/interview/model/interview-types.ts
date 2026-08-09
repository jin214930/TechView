export type InterviewTurnType = "CORE" | "FOLLOW_UP";

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
