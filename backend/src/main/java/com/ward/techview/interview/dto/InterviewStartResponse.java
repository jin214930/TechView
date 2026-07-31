package com.ward.techview.interview.dto;

import com.ward.techview.interview.entity.InterviewTurn;
import com.ward.techview.interview.entity.InterviewTurnType;

public record InterviewStartResponse(
        boolean resumed,
        Long sessionId,
        Long turnId,
        int turnOrder,
        InterviewTurnType turnType,
        String question
) {
    public static InterviewStartResponse of(boolean resumed, InterviewTurn turn) {
        return new InterviewStartResponse(
                resumed,
                turn.getSession().getId(),
                turn.getId(),
                turn.getTurnOrder(),
                turn.getTurnType(),
                extractQuestion(turn)
        );
    }

    private static String extractQuestion(InterviewTurn turn) {
        return switch (turn.getTurnType()) {
            case CORE -> turn.getInterviewCoreQuestion().getContent();
            case FOLLOW_UP -> turn.getFollowUpQuestion().getContent();
        };
    }
}
