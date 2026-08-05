package com.ward.techview.processing.repository;

import com.ward.techview.processing.entity.ProcessingJob;
import com.ward.techview.processing.entity.ProcessingJobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {
    @Query("""
            SELECT job
            FROM ProcessingJob job
            WHERE job.attemptCount < job.maxAttempts
              AND (
                    (
                        job.status IN :waitingStatuses
                        AND job.nextAttemptAt <= :now
                    )
                    OR
                    (
                        job.status = :processingStatus
                        AND job.leaseExpiresAt <= :now
                    )
              )
            ORDER BY job.id
            """)
    List<ProcessingJob> findDispatchableJobs(
            @Param("waitingStatuses")
            Collection<ProcessingJobStatus> waitingStatuses,
            @Param("processingStatus")
            ProcessingJobStatus processingStatus,
            @Param("now")
            OffsetDateTime now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT job
            FROM ProcessingJob job
            WHERE job.status = :processingStatus
              AND job.attemptCount >= job.maxAttempts
              AND job.leaseExpiresAt <= :now
            ORDER BY job.id
            """)
    List<ProcessingJob> findExhaustedExpiredJobs(
            @Param("processingStatus")
            ProcessingJobStatus processingStatus,
            @Param("now")
            OffsetDateTime now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT job
            FROM ProcessingJob job
            WHERE job.interviewSessionId = :interviewSessionId
            ORDER BY job.id
            """)
    List<ProcessingJob> findAllByInterviewSessionIdForUpdate(@Param("interviewSessionId") Long interviewSessionId);
}
