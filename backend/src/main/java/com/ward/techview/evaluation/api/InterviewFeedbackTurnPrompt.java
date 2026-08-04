package com.ward.techview.evaluation.api;

import java.util.List;
import java.util.Objects;

public record InterviewFeedbackTurnPrompt(
        Long answerId,
        int turnOrder,
        String category,
        String coreQuestion,
        String followUpQuestion,
        String transcript,
        String analysisSummary,
        List<String> analysisStrengths,
        List<String> analysisImprovements
) {
    private static final int MAX_FEEDBACK_ITEM_COUNT = 3;

    public InterviewFeedbackTurnPrompt {
        answerId = Objects.requireNonNull(answerId, "답변 ID는 필수입니다.");

        if (turnOrder <= 0) {
            throw new IllegalArgumentException("턴 순서는 0보다 커야 합니다.");
        }

        category = requireText(category, "카테고리는 필수입니다.");
        coreQuestion = requireText(coreQuestion, "핵심 질문은 필수입니다.");
        transcript = requireText(transcript, "전사문은 필수입니다.");
        analysisSummary = requireText(analysisSummary, "답변 분석 요약은 필수입니다.");

        if (followUpQuestion != null) {
            followUpQuestion = requireText(followUpQuestion, "꼬리 질문은 비어 있을 수 없습니다.");
        }

        analysisStrengths = validateFeedbackItems(analysisStrengths, "답변 분석 강점");
        analysisImprovements = validateFeedbackItems(analysisImprovements, "답변 분석 개선점");
    }

    private static List<String> validateFeedbackItems(List<String> items, String fieldName) {
        Objects.requireNonNull(items, fieldName + " 목록은 필수입니다.");

        if (items.size() > MAX_FEEDBACK_ITEM_COUNT) {
            throw new IllegalArgumentException(fieldName + "은 최대 " + MAX_FEEDBACK_ITEM_COUNT + "개까지 포함할 수 있습니다."
            );
        }

        return items.stream()
                .map(item -> requireText(item, fieldName + " 내용은 비어 있을 수 없습니다."))
                .toList();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.strip();
    }
}
