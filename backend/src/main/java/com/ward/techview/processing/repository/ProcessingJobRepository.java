package com.ward.techview.processing.repository;

import com.ward.techview.processing.entity.ProcessingJob;
import com.ward.techview.processing.entity.ProcessingJobType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {
    Optional<ProcessingJob> findByAnswerIdAndJobType(Long answerId, ProcessingJobType jobType);

    boolean existsByAnswerIdAndJobType(Long answerId, ProcessingJobType jobType);
}
