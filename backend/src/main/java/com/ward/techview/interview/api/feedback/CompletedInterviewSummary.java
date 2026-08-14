package com.ward.techview.interview.api.feedback;

import java.time.OffsetDateTime;
import java.util.Objects;

public record CompletedInterviewSummary(
        Long sessionId,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
    public CompletedInterviewSummary {
        Objects.requireNonNull(sessionId, "면접 세션 ID는 필수입니다.");
        Objects.requireNonNull(startedAt, "면접 시작 시각은 필수입니다.");
        Objects.requireNonNull(completedAt, "면접 완료 시각은 필수입니다.");
    }
}
