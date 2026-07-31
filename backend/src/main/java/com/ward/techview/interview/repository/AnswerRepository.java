package com.ward.techview.interview.repository;

import com.ward.techview.interview.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    Optional<Answer> findByInterviewTurnId(Long interviewTurnId);
}
