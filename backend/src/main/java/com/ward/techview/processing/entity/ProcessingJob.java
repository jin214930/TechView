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
                ),
                @CheckConstraint(
                        name = "chk_processing_job_target",
                        constraint = """
                                (
                                    job_type IN (
                                        'STT_TRANSCRIPTION',
                                        'ANSWER_ANALYSIS'
                                    )
                                    AND answer_id IS NOT NULL
                                )
                                OR
                                (
                                    job_type = 'FINAL_FEEDBACK_GENERATION'
                                    AND answer_id IS NULL
                                )
                                """
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessingJob extends BaseEntity {
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    /*
     * DB FK ON DELETE CASCADE:
     * 면접 세션 삭제 시 관련 처리 작업도 함께 삭제된다.
     */
    @Column(nullable = false)
    private Long interviewSessionId;

    /*
     * DB FK ON DELETE CASCADE:
     * 답변 단위 작업일 때만 값이 존재한다.
     * 답변 삭제 시 관련 처리 작업도 함께 삭제된다.
     */
    private Long answerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ProcessingJobType jobType;

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

    private ProcessingJob(
            Long interviewSessionId,
            Long answerId,
            ProcessingJobType jobType,
            OffsetDateTime queuedAt
    ) {
        this.interviewSessionId = Objects.requireNonNull(
                interviewSessionId,
                "면접 세션 ID는 필수입니다."
        );
        this.jobType = Objects.requireNonNull(
                jobType,
                "작업 유형은 필수입니다."
        );
        this.nextAttemptAt = Objects.requireNonNull(
                queuedAt,
                "작업 실행 예정 시각은 필수입니다."
        );

        validateTarget(jobType, answerId);

        this.answerId = answerId;
        this.status = ProcessingJobStatus.QUEUED;
        this.attemptCount = 0;
        this.maxAttempts = DEFAULT_MAX_ATTEMPTS;
    }

    public static ProcessingJob createSttTranscription(
            Long interviewSessionId,
            Long answerId,
            OffsetDateTime queuedAt
    ) {
        return new ProcessingJob(
                interviewSessionId,
                answerId,
                ProcessingJobType.STT_TRANSCRIPTION,
                queuedAt
        );
    }

    public static ProcessingJob createAnswerAnalysis(
            Long interviewSessionId,
            Long answerId,
            OffsetDateTime queuedAt
    ) {
        return new ProcessingJob(
                interviewSessionId,
                answerId,
                ProcessingJobType.ANSWER_ANALYSIS,
                queuedAt
        );
    }

    public static ProcessingJob createFinalFeedbackGeneration(
            Long interviewSessionId,
            OffsetDateTime queuedAt
    ) {
        return new ProcessingJob(
                interviewSessionId,
                null,
                ProcessingJobType.FINAL_FEEDBACK_GENERATION,
                queuedAt
        );
    }

    private void validateTarget(
            ProcessingJobType jobType,
            Long answerId
    ) {
        switch (jobType) {
            case STT_TRANSCRIPTION, ANSWER_ANALYSIS -> {
                if (answerId == null) {
                    throw new IllegalArgumentException(
                            "답변 단위 작업에는 답변 ID가 필요합니다."
                    );
                }
            }

            case FINAL_FEEDBACK_GENERATION -> {
                if (answerId != null) {
                    throw new IllegalArgumentException(
                            "최종 피드백 생성 작업에는 답변 ID를 지정할 수 없습니다."
                    );
                }
            }
        }
    }
}
