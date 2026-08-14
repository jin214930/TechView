package com.ward.techview.interview.api.answer;

public record AnswerInfo(
        Long answerId,
        Long interviewSessionId,
        String audioObjectKey,
        String audioContentType
) {
}
