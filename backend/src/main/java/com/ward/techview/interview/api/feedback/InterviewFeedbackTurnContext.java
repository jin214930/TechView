package com.ward.techview.interview.api.feedback;

public record InterviewFeedbackTurnContext(
        Long answerId,
        int turnOrder,
        String category,
        String coreQuestion,
        String followUpQuestion,
        String transcript
) {
}
