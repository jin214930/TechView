package com.ward.techview.processing.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "answer_analysis_processing_job",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_answer_analysis_processing_job_answer_id",
                        columnNames = "answer_id"
                )
        }
)
@PrimaryKeyJoinColumn(
        name = "processing_job_id",
        foreignKey = @ForeignKey(name = "fk_answer_analysis_processing_job_processing_job")
)
@DiscriminatorValue("ANSWER_ANALYSIS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnswerAnalysisProcessingJob extends ProcessingJob {

    @Column(nullable = false)
    private Long answerId;

    private AnswerAnalysisProcessingJob(
            Long interviewSessionId,
            Long answerId,
            OffsetDateTime queuedAt
    ) {
        super(interviewSessionId, queuedAt);
        this.answerId = Objects.requireNonNull(answerId, "답변 ID는 필수입니다.");
    }

    public static AnswerAnalysisProcessingJob create(
            Long interviewSessionId,
            Long answerId,
            OffsetDateTime queuedAt
    ) {
        return new AnswerAnalysisProcessingJob(interviewSessionId, answerId, queuedAt);
    }

    @Override
    public ProcessingJobType getJobType() {
        return ProcessingJobType.ANSWER_ANALYSIS;
    }
}
