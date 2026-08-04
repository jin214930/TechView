package com.ward.techview.interview.dto;

import com.ward.techview.interview.entity.InterviewTurn;
import com.ward.techview.interview.entity.InterviewTurnType;

public record InterviewProgressResponse(
        Long sessionId,
        InterviewProgressStatus status,
        Long turnId,
        Integer turnOrder,
        InterviewTurnType turnType,
        String question,
        String errorCode,
        String errorMessage
) {
    public static InterviewProgressResponse processing(Long sessionId) {
        return new InterviewProgressResponse(
                sessionId,
                InterviewProgressStatus.PROCESSING,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static InterviewProgressResponse completed(Long sessionId) {
        return new InterviewProgressResponse(
                sessionId,
                InterviewProgressStatus.COMPLETED,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static InterviewProgressResponse turnReady(
            Long sessionId,
            InterviewTurn turn
    ) {
        return new InterviewProgressResponse(
                sessionId,
                InterviewProgressStatus.TURN_READY,
                turn.getId(),
                turn.getTurnOrder(),
                turn.getTurnType(),
                turn.getQuestionContent(),
                null,
                null
        );
    }

    public static InterviewProgressResponse failed(
            Long sessionId,
            String errorCode,
            String errorMessage
    ) {
        return new InterviewProgressResponse(
                sessionId,
                InterviewProgressStatus.FAILED,
                null,
                null,
                null,
                null,
                errorCode,
                errorMessage
        );
    }
}
