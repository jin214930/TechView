package com.ward.techview.processing.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "stt_processing_job",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stt_processing_job_answer_id",
                        columnNames = "answer_id"
                )
        }
)
@PrimaryKeyJoinColumn(
        name = "processing_job_id",
        foreignKey = @ForeignKey(name = "fk_stt_processing_job_processing_job")
)
@DiscriminatorValue("STT_TRANSCRIPTION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SttProcessingJob extends ProcessingJob {
    private static final Duration FAILED_AUDIO_RETENTION_PERIOD = Duration.ofHours(24);

    @Column(nullable = false)
    private Long answerId;

    @Column(nullable = false, length = 1024)
    private String audioObjectKey;

    /*
     * STT 워커는 음성 객체 조회 시 이 값을 If-Match 조건으로 사용한다.
     * 검증 이후 같은 객체 키가 덮어써져도 검증하지 않은 바이트를 처리하지 않는다.
     */
    @Column(nullable = false)
    private String sourceObjectEtag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SttProcessingStage processingStage;

    private OffsetDateTime audioCleanupDueAt;

    private OffsetDateTime audioDeletedAt;

    private SttProcessingJob(
            Long interviewSessionId,
            Long answerId,
            String audioObjectKey,
            String sourceObjectEtag,
            OffsetDateTime queuedAt
    ) {
        super(interviewSessionId, queuedAt);
        this.answerId = Objects.requireNonNull(answerId, "답변 ID는 필수입니다.");
        this.audioObjectKey = requireText(audioObjectKey, "음성 객체 키는 필수입니다.");
        this.sourceObjectEtag = requireText(sourceObjectEtag, "STT 작업에는 검증된 음성 객체 ETag가 필요합니다.");
        this.processingStage = SttProcessingStage.TRANSCRIPTION;
    }

    public static SttProcessingJob create(
            Long interviewSessionId,
            Long answerId,
            String audioObjectKey,
            String sourceObjectEtag,
            OffsetDateTime queuedAt
    ) {
        return new SttProcessingJob(
                interviewSessionId,
                answerId,
                audioObjectKey,
                sourceObjectEtag,
                queuedAt
        );
    }

    @Override
    public ProcessingJobType getJobType() {
        return ProcessingJobType.STT_TRANSCRIPTION;
    }

    public void advanceToCleanup() {
        if (getStatus() != ProcessingJobStatus.PROCESSING) {
            throw new IllegalStateException("처리 중인 STT 작업만 정리 단계로 전환할 수 있습니다.");
        }

        if (processingStage != SttProcessingStage.TRANSCRIPTION) {
            throw new IllegalStateException("전사 단계의 STT 작업만 정리 단계로 전환할 수 있습니다.");
        }

        processingStage = SttProcessingStage.CLEANUP;
    }

    public void scheduleAudioCleanup(OffsetDateTime failedAt) {
        if (getStatus() != ProcessingJobStatus.FAILED) {
            throw new IllegalStateException("실패한 STT 작업만 음성 정리를 예약할 수 있습니다.");
        }

        Objects.requireNonNull(failedAt, "STT 작업 실패 시각은 필수입니다.");

        if (audioCleanupDueAt != null) {
            return;
        }

        audioCleanupDueAt = failedAt.plus(FAILED_AUDIO_RETENTION_PERIOD);
    }

    public void markAudioDeleted(OffsetDateTime deletedAt) {
        if (getStatus() != ProcessingJobStatus.FAILED) {
            throw new IllegalStateException("실패한 STT 작업만 음성 삭제 완료 처리할 수 있습니다.");
        }

        Objects.requireNonNull(deletedAt, "음성 삭제 시각은 필수입니다.");

        if (audioCleanupDueAt == null) {
            throw new IllegalStateException("음성 정리가 예약되지 않았습니다.");
        }

        if (deletedAt.isBefore(audioCleanupDueAt)) {
            throw new IllegalArgumentException("음성 정리 예정 시각 전에는 삭제 완료 처리할 수 없습니다.");
        }

        if (audioDeletedAt != null) {
            return;
        }

        audioDeletedAt = deletedAt;
    }
}
