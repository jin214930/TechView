package com.ward.techview.processing.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(nullable = false)
    private Long answerId;

    @Column(nullable = false, length = 1024)
    private String audioObjectKey;

    /*
     * STT 워커는 음성 객체 조회 시 이 값을 If-Match 조건으로 사용한다.
     * 검증 이후 같은 객체 키가 덮어써져도 검증하지 않은 바이트를 처리하지 않는다.
     */
    @Column(nullable = false, length = 255)
    private String sourceObjectEtag;

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
        this.sourceObjectEtag = requireText(
                sourceObjectEtag,
                "STT 작업에는 검증된 음성 객체 ETag가 필요합니다."
        );
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
}
