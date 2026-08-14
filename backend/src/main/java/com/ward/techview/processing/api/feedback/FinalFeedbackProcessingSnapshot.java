package com.ward.techview.processing.api.feedback;

import java.util.Objects;

public record FinalFeedbackProcessingSnapshot(
        FinalFeedbackProcessingStatus status
) {
    public FinalFeedbackProcessingSnapshot {
        Objects.requireNonNull(status, "종합 피드백 처리 상태는 필수입니다.");
    }
}
