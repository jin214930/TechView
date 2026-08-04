package com.ward.techview.interview.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterviewTurnTest {

    @Test
    @DisplayName("핵심 질문 턴의 질문 내용을 반환한다")
    void returnsCoreQuestionContent() {
        InterviewSession session = session();
        InterviewTurn turn = InterviewTurn.createCore(
                session,
                1,
                coreQuestion(session)
        );

        assertEquals("핵심 질문", turn.getQuestionContent());
    }

    @Test
    @DisplayName("꼬리 질문 턴의 질문 내용을 반환한다")
    void returnsFollowUpQuestionContent() {
        InterviewSession session = session();
        FollowUpQuestion followUpQuestion = FollowUpQuestion.create(
                coreQuestion(session),
                "꼬리 질문"
        );
        InterviewTurn turn = InterviewTurn.createFollowUp(
                session,
                2,
                followUpQuestion
        );

        assertEquals("꼬리 질문", turn.getQuestionContent());
    }

    private static InterviewSession session() {
        return InterviewSession.create(
                1L,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private static InterviewCoreQuestion coreQuestion(InterviewSession session) {
        return InterviewCoreQuestion.create(
                session,
                10L,
                "핵심 질문",
                "JAVA",
                1
        );
    }
}
