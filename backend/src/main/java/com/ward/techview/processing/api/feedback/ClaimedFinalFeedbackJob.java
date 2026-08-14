package com.ward.techview.processing.api.feedback;

public record ClaimedFinalFeedbackJob(
        Long processingJobId,
        Long interviewSessionId,
        int attemptCount
) {
}
