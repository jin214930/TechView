package com.ward.techview.processing.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "final_feedback_processing_job")
@PrimaryKeyJoinColumn(
        name = "processing_job_id",
        foreignKey = @ForeignKey(name = "fk_final_feedback_processing_job_processing_job")
)
@DiscriminatorValue("FINAL_FEEDBACK_GENERATION")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinalFeedbackProcessingJob extends ProcessingJob {

    private FinalFeedbackProcessingJob(
            Long interviewSessionId,
            OffsetDateTime queuedAt
    ) {
        super(interviewSessionId, queuedAt);
    }

    public static FinalFeedbackProcessingJob create(
            Long interviewSessionId,
            OffsetDateTime queuedAt
    ) {
        return new FinalFeedbackProcessingJob(interviewSessionId, queuedAt);
    }

    @Override
    public ProcessingJobType getJobType() {
        return ProcessingJobType.FINAL_FEEDBACK_GENERATION;
    }
}
