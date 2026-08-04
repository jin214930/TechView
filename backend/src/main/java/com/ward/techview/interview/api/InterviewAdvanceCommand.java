package com.ward.techview.interview.api;

import java.time.OffsetDateTime;
import java.util.Objects;

public record InterviewAdvanceCommand(
        Long interviewSessionId,
        Long answerId,
        String followUpQuestion,
        OffsetDateTime analyzedAt
) {
    public InterviewAdvanceCommand {
        interviewSessionId = Objects.requireNonNull(interviewSessionId, "면접 세션 ID는 필수입니다.");

        answerId = Objects.requireNonNull(answerId, "답변 ID는 필수입니다.");

        analyzedAt = Objects.requireNonNull(analyzedAt, "분석 완료 시각은 필수입니다.");

        if (followUpQuestion != null) {
            if (followUpQuestion.isBlank()) {
                throw new IllegalArgumentException("꼬리 질문은 비어 있을 수 없습니다.");
            }

            followUpQuestion = followUpQuestion.strip();
        }
    }

    public boolean requiresFollowUp() {
        return followUpQuestion != null;
    }
}
