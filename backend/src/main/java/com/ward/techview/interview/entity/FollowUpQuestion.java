package com.ward.techview.interview.entity;

import com.ward.techview.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "follow_up_question")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FollowUpQuestion extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY)
    // DB FK ON DELETE CASCADE: 핵심 질문 스냅샷 삭제 시 연결된 꼬리 질문도 삭제한다.
    @JoinColumn(name = "interview_core_question_id", nullable = false, unique = true)
    private InterviewCoreQuestion coreQuestion;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private FollowUpQuestion(InterviewCoreQuestion coreQuestion, String content) {
        this.coreQuestion = Objects.requireNonNull(coreQuestion, "핵심 질문은 필수입니다.");

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("꼬리 질문 내용은 비어 있을 수 없습니다.");
        }

        this.content = content;
    }

    public static FollowUpQuestion create(InterviewCoreQuestion coreQuestion, String content) {
        return new FollowUpQuestion(coreQuestion, content);
    }

    public boolean belongsTo(InterviewSession session) {
        return coreQuestion.belongsTo(session);
    }
}
