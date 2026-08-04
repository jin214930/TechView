package com.ward.techview.processing.repository;

import com.ward.techview.processing.entity.AnswerAnalysisProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnswerAnalysisProcessingJobRepository extends JpaRepository<AnswerAnalysisProcessingJob, Long> {
    Optional<AnswerAnalysisProcessingJob> findByAnswerId(Long answerId);
}
