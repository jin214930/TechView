package com.ward.techview.evaluation.api;

public record AnswerAnalysisPrompt(
        String category,
        String coreQuestion,
        String followUpQuestion,
        String transcript,
        boolean followUpAllowed
) {
    public AnswerAnalysisPrompt {
        category = requireText(category, "카테고리는 필수입니다.");
        coreQuestion = requireText(coreQuestion, "핵심 질문은 필수입니다.");
        transcript = requireText(transcript, "전사문은 필수입니다.");

        if (followUpAllowed) {
            if (followUpQuestion != null) {
                throw new IllegalArgumentException("꼬리 질문 생성이 가능한 핵심 질문에는 기존 꼬리 질문이 없어야 합니다.");
            }
        } else {
            followUpQuestion = requireText(followUpQuestion, "꼬리 질문 답변 분석에는 꼬리 질문이 필요합니다.");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.strip();
    }
}
