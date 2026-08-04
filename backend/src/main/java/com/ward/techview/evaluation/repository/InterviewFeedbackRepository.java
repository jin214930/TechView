package com.ward.techview.evaluation.repository;

import com.ward.techview.evaluation.entity.InterviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, Long> {
    Optional<InterviewFeedback> findByInterviewSessionId(Long interviewSessionId);
}
