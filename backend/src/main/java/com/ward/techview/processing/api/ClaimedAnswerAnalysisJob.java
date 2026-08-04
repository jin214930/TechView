package com.ward.techview.processing.api;

public record ClaimedAnswerAnalysisJob(
        Long processingJobId,
        Long interviewSessionId,
        Long answerId,
        int attemptCount
) {
}
