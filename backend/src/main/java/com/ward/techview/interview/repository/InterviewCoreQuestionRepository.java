package com.ward.techview.interview.repository;

import com.ward.techview.interview.entity.InterviewCoreQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewCoreQuestionRepository extends JpaRepository<InterviewCoreQuestion, Long> {
    Optional<InterviewCoreQuestion> findBySessionIdAndQuestionOrder(Long sessionId, int questionOrder);
}
