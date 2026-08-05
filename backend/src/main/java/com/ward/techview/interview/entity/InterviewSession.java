package com.ward.techview.interview.entity;

import com.ward.techview.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "interview_session",
        indexes = {
                @Index(
                        name = "idx_interview_session_member_status_started_at",
                        columnList = "member_id, status, started_at"
                ),
                @Index(
                        name = "idx_interview_session_member_status_id",
                        columnList = "member_id, status, id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewSession extends BaseEntity {
    // DB FK ON DELETE CASCADE: 회원 탈퇴 시 면접 세션과 모든 하위 데이터를 함께 삭제한다.
    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewStatus status;

    @Column(nullable = false)
    private OffsetDateTime startedAt;

    private OffsetDateTime completedAt;

    @Column(nullable = false)
    private int currentTurnOrder;

    private InterviewSession(Long memberId, OffsetDateTime startedAt) {
        this.memberId = Objects.requireNonNull(memberId, "회원 ID는 필수입니다.");
        this.startedAt = Objects.requireNonNull(startedAt, "면접 시작 시각은 필수입니다.");
        this.status = InterviewStatus.IN_PROGRESS;
        this.currentTurnOrder = 1;
    }

    public static InterviewSession create(Long memberId, OffsetDateTime startedAt) {
        return new InterviewSession(memberId, startedAt);
    }

    public int advanceTurn() {
        validateInProgress();

        if (currentTurnOrder >= 10) {
            throw new IllegalStateException("면접 턴 순서는 10을 초과할 수 없습니다.");
        }

        return ++currentTurnOrder;
    }

    public void complete(OffsetDateTime completedAt) {
        validateInProgress();

        this.completedAt = Objects.requireNonNull(completedAt, "면접 완료 시각은 필수입니다.");
        this.status = InterviewStatus.COMPLETED;
    }

    private void validateInProgress() {
        if (status != InterviewStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행 중인 면접 세션만 변경할 수 있습니다.");
        }
    }

    public boolean isExpired(OffsetDateTime cutoff) {
        Objects.requireNonNull(cutoff, "면접 만료 기준 시각은 필수입니다.");

        return status == InterviewStatus.IN_PROGRESS && startedAt.isBefore(cutoff);
    }
}
