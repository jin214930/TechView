import type { InterviewStart } from "@/features/interview/model/interview-types";

export const INTERVIEW_START_STORAGE_KEY = "techview.interview.start";

export function saveInterviewStart(start: InterviewStart): void {
  window.sessionStorage.setItem(
    INTERVIEW_START_STORAGE_KEY,
    JSON.stringify(start),
  );
}

export function clearInterviewStart(): void {
  window.sessionStorage.removeItem(INTERVIEW_START_STORAGE_KEY);
}
