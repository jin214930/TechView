package com.ward.techview.processing.repository;

import com.ward.techview.processing.entity.SttProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SttProcessingJobRepository extends JpaRepository<SttProcessingJob, Long> {
    Optional<SttProcessingJob> findByAnswerId(Long answerId);

    boolean existsByAnswerId(Long answerId);
}
