package com.ward.techview.processing.repository;

import com.ward.techview.processing.entity.SttProcessingJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface SttProcessingJobRepository extends JpaRepository<SttProcessingJob, Long> {
    Optional<SttProcessingJob> findByAnswerId(Long answerId);

    boolean existsByAnswerId(Long answerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT job
            FROM SttProcessingJob job
            WHERE job.id = :processingJobId
            """)
    Optional<SttProcessingJob> findByIdForUpdate(@Param("processingJobId") Long processingJobId);

    @Query("""
            SELECT job
            FROM SttProcessingJob job
            WHERE job.status = com.ward.techview.processing.entity.ProcessingJobStatus.FAILED
              AND job.audioCleanupDueAt <= :now
              AND job.audioDeletedAt IS NULL
              AND job.id > :afterProcessingJobId
            ORDER BY job.id
            """)
    List<SttProcessingJob> findDueAudioCleanupJobs(
            @Param("now") OffsetDateTime now,
            @Param("afterProcessingJobId") Long afterProcessingJobId,
            Pageable pageable
    );
}
