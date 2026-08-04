package com.ward.techview.processing.repository;

import com.ward.techview.processing.entity.FinalFeedbackProcessingJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FinalFeedbackProcessingJobRepository extends JpaRepository<FinalFeedbackProcessingJob, Long> {
    Optional<FinalFeedbackProcessingJob> findByInterviewSessionId(Long interviewSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT job
            FROM FinalFeedbackProcessingJob job
            WHERE job.id = :processingJobId
            """)
    Optional<FinalFeedbackProcessingJob> findByIdForUpdate(@Param("processingJobId") Long processingJobId);
}
