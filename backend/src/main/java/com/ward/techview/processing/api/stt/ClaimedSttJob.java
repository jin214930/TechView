package com.ward.techview.processing.api.stt;

public record ClaimedSttJob(
        Long processingJobId,
        Long interviewSessionId,
        Long answerId,
        String audioObjectKey,
        String sourceObjectEtag,
        boolean cleanupPending,
        int attemptCount
) {
}
