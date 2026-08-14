package com.ward.techview.evaluation.api.feedback;

public record CategoryFeedback(
        String category,
        String assessment,
        String recommendation
) {
    public CategoryFeedback {
        category = requireText(category, "피드백 카테고리는 필수입니다.");
        assessment = requireText(assessment, "카테고리 평가는 필수입니다.");
        recommendation = requireText(recommendation, "카테고리 학습 추천은 필수입니다.");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.strip();
    }
}
