package com.ward.techview.evaluation.dto;

import com.ward.techview.evaluation.api.feedback.CategoryFeedback;

public record CategoryFeedbackResponse(
        String category,
        String assessment,
        String recommendation
) {
    public static CategoryFeedbackResponse from(CategoryFeedback feedback) {
        return new CategoryFeedbackResponse(
                feedback.category(),
                feedback.assessment(),
                feedback.recommendation()
        );
    }
}
