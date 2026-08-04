package com.ward.techview.interview.entity;

import com.ward.techview.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(
        name = "interview_turn",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_interview_turn_session_order",
                        columnNames = {"interview_session_id", "turn_order"}
                )
        },
        check = {
                @CheckConstraint(
                        name = "chk_interview_turn_question_type",
                        constraint = """
                                (
                                    turn_type = 'CORE'
                                    AND interview_core_question_id IS NOT NULL
                                    AND follow_up_question_id IS NULL
                                )
                                OR
                                (
                                    turn_type = 'FOLLOW_UP'
                                    AND interview_core_question_id IS NULL
                                    AND follow_up_question_id IS NOT NULL
                                )
                                """
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewTurn extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    // DB FK ON DELETE CASCADE: 면접 세션 삭제 시 진행 턴도 삭제한다.
    @JoinColumn(name = "interview_session_id", nullable = false)
    private InterviewSession session;

    @Column(nullable = false)
    private int turnOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewTurnType turnType;

    @OneToOne(fetch = FetchType.LAZY)
    // DB FK ON DELETE CASCADE: 핵심 질문 스냅샷 삭제 시 해당 핵심 질문 턴도 삭제한다.
    @JoinColumn(name = "interview_core_question_id", unique = true)
    private InterviewCoreQuestion interviewCoreQuestion;

    @OneToOne(fetch = FetchType.LAZY)
    // DB FK ON DELETE CASCADE: 꼬리 질문 삭제 시 해당 꼬리 질문 턴도 삭제한다.
    @JoinColumn(name = "follow_up_question_id", unique = true)
    private FollowUpQuestion followUpQuestion;

    private InterviewTurn(InterviewSession session, int turnOrder, InterviewTurnType turnType, InterviewCoreQuestion coreQuestion, FollowUpQuestion followUpQuestion) {
        this.session = Objects.requireNonNull(session, "면접 세션은 필수입니다.");
        this.turnType = Objects.requireNonNull(turnType, "턴 타입은 필수입니다.");

        if (turnOrder < 1 || turnOrder > 10) {
            throw new IllegalArgumentException("턴 순서는 1부터 10까지입니다.");
        }

        validateQuestionReference(turnType, coreQuestion, followUpQuestion);

        this.turnOrder = turnOrder;
        this.interviewCoreQuestion = coreQuestion;
        this.followUpQuestion = followUpQuestion;
    }

    private void validateQuestionReference(
            InterviewTurnType turnType,
            InterviewCoreQuestion coreQuestion,
            FollowUpQuestion followUpQuestion
    ) {
        if (turnType == InterviewTurnType.CORE) {
            if (coreQuestion == null || followUpQuestion != null) {
                throw new IllegalArgumentException("핵심 턴에는 핵심 질문만 필요합니다.");
            }

            if (!coreQuestion.belongsTo(session)) {
                throw new IllegalArgumentException("다른 세션의 핵심 질문입니다.");
            }

            return;
        }

        if (followUpQuestion == null || coreQuestion != null) {
            throw new IllegalArgumentException("꼬리 질문 턴에는 꼬리 질문만 필요합니다.");
        }

        if (!followUpQuestion.belongsTo(session)) {
            throw new IllegalArgumentException("다른 세션의 꼬리 질문입니다.");
        }
    }

    public static InterviewTurn createCore(InterviewSession session, int turnOrder, InterviewCoreQuestion interviewCoreQuestion) {
        return new InterviewTurn(session, turnOrder, InterviewTurnType.CORE, interviewCoreQuestion, null);
    }

    public static InterviewTurn createFollowUp(InterviewSession session, int turnOrder, FollowUpQuestion followUpQuestion) {
        return new InterviewTurn(session, turnOrder, InterviewTurnType.FOLLOW_UP, null, followUpQuestion);
    }

    public String getQuestionContent() {
        return switch (turnType) {
            case CORE -> interviewCoreQuestion.getContent();
            case FOLLOW_UP -> followUpQuestion.getContent();
        };
    }
}
