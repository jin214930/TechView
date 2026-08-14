package com.ward.techview.evaluation.api.feedback;

import java.util.List;
import java.util.Objects;

public record InterviewFeedbackPrompt(
        Long interviewSessionId,
        List<InterviewFeedbackTurnPrompt> turns
) {
    public InterviewFeedbackPrompt {
        interviewSessionId = Objects.requireNonNull(interviewSessionId, "면접 세션 ID는 필수입니다.");

        Objects.requireNonNull(turns, "면접 턴 목록은 필수입니다.");

        if (turns.isEmpty()) {
            throw new IllegalArgumentException("면접 턴 목록은 비어 있을 수 없습니다.");
        }

        if (turns.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("면접 턴은 null일 수 없습니다.");
        }

        turns = List.copyOf(turns);

        long distinctTurnCount = turns.stream()
                .map(InterviewFeedbackTurnPrompt::turnOrder)
                .distinct()
                .count();

        if (distinctTurnCount != turns.size()) {
            throw new IllegalArgumentException("면접 턴 순서는 중복될 수 없습니다.");
        }
    }
}
