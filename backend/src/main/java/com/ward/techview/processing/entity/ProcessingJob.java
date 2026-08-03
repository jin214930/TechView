package com.ward.techview.processing.entity;

import com.ward.techview.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(
        indexes = {
                @Index(
                        name = "idx_processing_job_interview_session_id",
                        columnList = "interview_session_id"
                ),
                @Index(
                        name = "idx_processing_job_status_next_attempt_at",
                        columnList = "status, next_attempt_at"
                )
        },
        check = {
                @CheckConstraint(
                        name = "chk_processing_job_attempt_count",
                        constraint = """
                                attempt_count >= 0
                                AND max_attempts > 0
                                AND attempt_count <= max_attempts
                                """
                )
        }
)
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(
        name = "job_type",
        discriminatorType = DiscriminatorType.STRING,
        length = 50
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ProcessingJob extends BaseEntity {
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    /*
     * DB FK ON DELETE CASCADE:
     * 면접 세션 삭제 시 부모 작업과 하위 작업 상세가 함께 삭제된다.
     */
    @Column(nullable = false)
    private Long interviewSessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProcessingJobStatus status;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private int maxAttempts;

    private OffsetDateTime nextAttemptAt;

    private OffsetDateTime leaseExpiresAt;

    @Column(length = 100)
    private String errorCode;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    protected ProcessingJob(
            Long interviewSessionId,
            OffsetDateTime queuedAt
    ) {
        this.interviewSessionId = Objects.requireNonNull(
                interviewSessionId,
                "면접 세션 ID는 필수입니다."
        );
        this.nextAttemptAt = Objects.requireNonNull(
                queuedAt,
                "작업 실행 예정 시각은 필수입니다."
        );
        this.status = ProcessingJobStatus.QUEUED;
        this.attemptCount = 0;
        this.maxAttempts = DEFAULT_MAX_ATTEMPTS;
    }

    @Transient
    public abstract ProcessingJobType getJobType();

    protected static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }
}
