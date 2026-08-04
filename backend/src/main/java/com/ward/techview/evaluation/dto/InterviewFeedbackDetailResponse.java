package com.ward.techview.evaluation.dto;

import com.ward.techview.evaluation.entity.InterviewFeedback;

import java.util.List;

public record InterviewFeedbackDetailResponse(
        String overallSummary,
        List<String> strengths,
        List<String> improvements,
        List<CategoryFeedbackResponse> categoryFeedbacks,
        List<String> recommendedTopics
) {
    public static InterviewFeedbackDetailResponse from(InterviewFeedback feedback) {
        List<CategoryFeedbackResponse> categoryFeedbacks =
                feedback.getCategoryFeedbacks()
                        .stream()
                        .map(CategoryFeedbackResponse::from)
                        .toList();

        return new InterviewFeedbackDetailResponse(
                feedback.getOverallSummary(),
                List.copyOf(feedback.getStrengths()),
                List.copyOf(feedback.getImprovements()),
                categoryFeedbacks,
                List.copyOf(feedback.getRecommendedTopics())
        );
    }
}
