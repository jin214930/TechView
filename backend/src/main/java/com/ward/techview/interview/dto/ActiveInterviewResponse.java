package com.ward.techview.interview.dto;

import com.ward.techview.interview.entity.InterviewSession;

import java.time.OffsetDateTime;

public record ActiveInterviewResponse(
        boolean hasActiveSession,
        Long sessionId,
        OffsetDateTime startedAt,
        Integer currentTurnOrder
) {
    public static ActiveInterviewResponse from(InterviewSession session) {
        return new ActiveInterviewResponse(
                true,
                session.getId(),
                session.getStartedAt(),
                session.getCurrentTurnOrder()
        );
    }

    public static ActiveInterviewResponse none() {
        return new ActiveInterviewResponse(false, null, null, null);
    }
}
