package com.ward.techview.interview.entity;

import com.ward.techview.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(
        name = "interview_core_question",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_interview_core_question_session_order",
                        columnNames = {"interview_session_id", "question_order"}
                ),
                @UniqueConstraint(
                        name = "uk_interview_core_question_session_category",
                        columnNames = {"interview_session_id", "category"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewCoreQuestion extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    // DB FK ON DELETE CASCADE: 면접 세션 삭제 시 선택된 핵심 질문 스냅샷도 삭제한다.
    @JoinColumn(name = "interview_session_id", nullable = false)
    private InterviewSession session;

    // DB FK ON DELETE SET NULL: 원본 질문이 삭제되어도 당시 면접 질문 스냅샷은 보존한다.
    private Long sourceQuestionId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false)
    private int questionOrder;

    private InterviewCoreQuestion(InterviewSession session, Long sourceQuestionId, String content, String category, int questionOrder) {
        this.session = Objects.requireNonNull(session, "면접 세션은 필수입니다.");
        this.sourceQuestionId = Objects.requireNonNull(sourceQuestionId, "원본 질문 ID는 필수입니다.");

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("질문 내용은 비어 있을 수 없습니다.");
        }

        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("질문 카테고리는 비어 있을 수 없습니다.");
        }

        if (questionOrder < 1 || questionOrder > 5) {
            throw new IllegalArgumentException("핵심 질문 순서는 1부터 5까지입니다.");
        }

        this.content = content;
        this.category = category;
        this.questionOrder = questionOrder;
    }

    public static InterviewCoreQuestion create(InterviewSession session, Long sourceQuestionId, String content, String category, int questionOrder) {
        return new InterviewCoreQuestion(session, sourceQuestionId, content, category, questionOrder);
    }

    public boolean belongsTo(InterviewSession target) {
        Objects.requireNonNull(target, "면접 세션은 필수입니다.");

        if (session == target) {
            return true;
        }

        return session.getId() != null
                && session.getId().equals(target.getId());
    }
}
