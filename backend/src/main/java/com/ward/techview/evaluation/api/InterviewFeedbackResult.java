package com.ward.techview.evaluation.api;

import java.util.List;
import java.util.Objects;

public record InterviewFeedbackResult(
        String overallSummary,
        List<String> strengths,
        List<String> improvements,
        List<CategoryFeedback> categoryFeedbacks,
        List<String> recommendedTopics,
        String model,
        String promptVersion
) {
    private static final int MIN_FEEDBACK_ITEM_COUNT = 1;
    private static final int MAX_FEEDBACK_ITEM_COUNT = 3;
    private static final int CATEGORY_FEEDBACK_COUNT = 5;

    public InterviewFeedbackResult {
        overallSummary = requireText(overallSummary, "전체 총평은 필수입니다.");

        strengths = validateFeedbackItems(strengths, "잘한 점");
        improvements = validateFeedbackItems(improvements, "개선할 점");
        recommendedTopics = validateFeedbackItems(recommendedTopics, "추천 학습 주제");
        categoryFeedbacks = validateCategoryFeedbacks(categoryFeedbacks);

        model = requireText(model, "모델명은 필수입니다.");
        promptVersion = requireText(promptVersion, "프롬프트 버전은 필수입니다.");
    }

    private static List<String> validateFeedbackItems(List<String> items, String fieldName) {
        Objects.requireNonNull(items, fieldName + " 목록은 필수입니다.");

        if (items.size() < MIN_FEEDBACK_ITEM_COUNT || items.size() > MAX_FEEDBACK_ITEM_COUNT) {
            throw new IllegalArgumentException(fieldName + "은 " + MIN_FEEDBACK_ITEM_COUNT + "개 이상 " + MAX_FEEDBACK_ITEM_COUNT + "개 이하여야 합니다.");
        }

        return items.stream()
                .map(item -> requireText(item, fieldName + " 내용은 비어 있을 수 없습니다."))
                .toList();
    }

    private static List<CategoryFeedback> validateCategoryFeedbacks(List<CategoryFeedback> feedbacks) {
        Objects.requireNonNull(feedbacks, "카테고리별 피드백 목록은 필수입니다.");

        if (feedbacks.size() != CATEGORY_FEEDBACK_COUNT) {
            throw new IllegalArgumentException("카테고리별 피드백은 정확히 " + CATEGORY_FEEDBACK_COUNT + "개여야 합니다.");
        }

        List<CategoryFeedback> copiedFeedbacks =
                feedbacks.stream()
                        .map(feedback -> Objects.requireNonNull(feedback, "카테고리별 피드백은 null일 수 없습니다."))
                        .toList();

        long categoryCount = copiedFeedbacks.stream()
                .map(CategoryFeedback::category)
                .distinct()
                .count();

        if (categoryCount != CATEGORY_FEEDBACK_COUNT) {
            throw new IllegalArgumentException("카테고리별 피드백의 카테고리는 중복될 수 없습니다.");
        }

        return copiedFeedbacks;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.strip();
    }
}
