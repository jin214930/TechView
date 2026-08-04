package com.ward.techview.evaluation.dto;

import com.ward.techview.interview.api.CompletedInterviewSummary;

import java.time.OffsetDateTime;
import java.util.Objects;

public record InterviewHistoryItemResponse(
        Long sessionId,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        InterviewFeedbackStatus feedbackStatus
) {
    public InterviewHistoryItemResponse {
        Objects.requireNonNull(sessionId, "면접 세션 ID는 필수입니다.");
        Objects.requireNonNull(startedAt, "면접 시작 시각은 필수입니다.");
        Objects.requireNonNull(completedAt, "면접 완료 시각은 필수입니다.");
        Objects.requireNonNull(feedbackStatus, "피드백 상태는 필수입니다.");
    }

    public static InterviewHistoryItemResponse of(CompletedInterviewSummary interview, InterviewFeedbackStatus feedbackStatus) {
        return new InterviewHistoryItemResponse(
                interview.sessionId(),
                interview.startedAt(),
                interview.completedAt(),
                feedbackStatus
        );
    }
}
