package com.ward.techview.evaluation.repository;

import com.ward.techview.evaluation.entity.InterviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, Long> {
}
