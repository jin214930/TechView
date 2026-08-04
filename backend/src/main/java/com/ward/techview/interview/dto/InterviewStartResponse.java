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
                turn.getQuestionContent()
        );
    }
}
