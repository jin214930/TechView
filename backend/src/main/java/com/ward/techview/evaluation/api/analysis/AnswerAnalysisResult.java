package com.ward.techview.evaluation.api.analysis;

import java.util.List;
import java.util.Objects;

public record AnswerAnalysisResult(
        String summary,
        List<String> strengths,
        List<String> improvements,
        FollowUpDecision followUpDecision,
        String followUpReason,
        String followUpQuestion,
        String model,
        String promptVersion
) {
    private static final int MAX_FEEDBACK_ITEM_COUNT = 3;

    public AnswerAnalysisResult {
        summary = requireText(summary, "답변 요약은 필수입니다.");
        strengths = validateFeedbackItems(strengths, "잘한 점");
        improvements = validateFeedbackItems(improvements, "개선할 점");
        followUpDecision = Objects.requireNonNull(followUpDecision, "꼬리 질문 결정은 필수입니다.");
        followUpReason = requireText(followUpReason, "꼬리 질문 판단 근거는 필수입니다.");
        model = requireText(model, "모델명은 필수입니다.");
        promptVersion = requireText(promptVersion, "프롬프트 버전은 필수입니다.");
        followUpQuestion = validateFollowUpQuestion(followUpDecision, followUpQuestion);
    }

    private static String validateFollowUpQuestion(FollowUpDecision decision, String question) {
        return switch (decision) {
            case ASK -> requireText(question, "ASK 결정에는 꼬리 질문이 필요합니다.");

            case SKIP -> {
                if (question != null) {
                    throw new IllegalArgumentException("SKIP 결정에는 꼬리 질문을 포함할 수 없습니다.");
                }

                yield null;
            }
        };
    }

    private static List<String> validateFeedbackItems(List<String> items, String fieldName) {
        Objects.requireNonNull(items, fieldName + " 목록은 필수입니다.");

        if (items.size() > MAX_FEEDBACK_ITEM_COUNT) {
            throw new IllegalArgumentException(fieldName + "은 최대 " + MAX_FEEDBACK_ITEM_COUNT + "개까지 포함할 수 있습니다.");
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
