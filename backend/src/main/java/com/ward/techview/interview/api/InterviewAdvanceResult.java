package com.ward.techview.interview.api;

public record InterviewAdvanceResult(
        boolean interviewCompleted
) {
    public static InterviewAdvanceResult continued() {
        return new InterviewAdvanceResult(false);
    }

    public static InterviewAdvanceResult completed() {
        return new InterviewAdvanceResult(true);
    }
}
