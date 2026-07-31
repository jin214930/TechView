package com.ward.techview.interview.api;

public record AnswerCreateCommand(
        Long memberId,
        Long sessionId,
        Long turnId,
        String audioObjectKey,
        String audioContentType,
        int audioDurationMs
) {
}
