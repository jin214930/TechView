package com.ward.techview.processing.repository;

import com.ward.techview.processing.entity.FinalFeedbackProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinalFeedbackProcessingJobRepository extends JpaRepository<FinalFeedbackProcessingJob, Long> {
    Optional<FinalFeedbackProcessingJob> findByInterviewSessionId(Long interviewSessionId);
}
