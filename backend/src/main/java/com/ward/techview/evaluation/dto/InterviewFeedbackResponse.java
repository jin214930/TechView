package com.ward.techview.evaluation.dto;

import com.ward.techview.evaluation.entity.InterviewFeedback;

public record InterviewFeedbackResponse(
        Long sessionId,
        InterviewFeedbackStatus status,
        InterviewFeedbackDetailResponse feedback,
        String errorMessage
) {
    private static final String GENERATION_FAILED_MESSAGE = "피드백을 제공할 수 없습니다.";

    public static InterviewFeedbackResponse processing(Long sessionId) {
        return new InterviewFeedbackResponse(
                sessionId,
                InterviewFeedbackStatus.PROCESSING,
                null,
                null
        );
    }

    public static InterviewFeedbackResponse failed(Long sessionId) {
        return new InterviewFeedbackResponse(
                sessionId,
                InterviewFeedbackStatus.FAILED,
                null,
                GENERATION_FAILED_MESSAGE
        );
    }

    public static InterviewFeedbackResponse completed(InterviewFeedback feedback) {
        return new InterviewFeedbackResponse(
                feedback.getInterviewSessionId(),
                InterviewFeedbackStatus.COMPLETED,
                InterviewFeedbackDetailResponse.from(feedback),
                null
        );
    }
}
