package com.ward.techview.interview.api.answer;

import java.time.OffsetDateTime;

public record AnswerTranscriptionCommand(
        Long interviewSessionId,
        Long answerId,
        String transcript,
        OffsetDateTime transcribedAt
) {
}
