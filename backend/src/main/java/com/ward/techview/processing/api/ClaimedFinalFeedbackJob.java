package com.ward.techview.processing.api;

public record ClaimedFinalFeedbackJob(
        Long processingJobId,
        Long interviewSessionId,
        int attemptCount
) {
}
