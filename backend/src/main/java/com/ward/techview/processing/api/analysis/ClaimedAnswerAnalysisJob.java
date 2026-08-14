package com.ward.techview.processing.api.analysis;

public record ClaimedAnswerAnalysisJob(
        Long processingJobId,
        Long interviewSessionId,
        Long answerId,
        int attemptCount
) {
}
