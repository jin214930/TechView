package com.ward.techview.interview.api;

public record AnswerInfo(
        Long answerId,
        Long interviewSessionId,
        String audioObjectKey,
        String audioContentType
) {
}
