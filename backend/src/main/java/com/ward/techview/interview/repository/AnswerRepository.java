package com.ward.techview.interview.repository;

import com.ward.techview.interview.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    boolean existsByInterviewTurnId(Long interviewTurnId);
}
