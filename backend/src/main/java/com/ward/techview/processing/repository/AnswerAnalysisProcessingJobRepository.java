package com.ward.techview.processing.repository;

import com.ward.techview.processing.entity.AnswerAnalysisProcessingJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AnswerAnalysisProcessingJobRepository extends JpaRepository<AnswerAnalysisProcessingJob, Long> {
    Optional<AnswerAnalysisProcessingJob> findByAnswerId(Long answerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT job
            FROM AnswerAnalysisProcessingJob job
            WHERE job.id = :processingJobId
            """)
    Optional<AnswerAnalysisProcessingJob> findByIdForUpdate(@Param("processingJobId") Long processingJobId);
}
