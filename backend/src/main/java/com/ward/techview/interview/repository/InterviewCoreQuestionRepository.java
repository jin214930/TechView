package com.ward.techview.interview.repository;

import com.ward.techview.interview.entity.InterviewCoreQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewCoreQuestionRepository extends JpaRepository<InterviewCoreQuestion, Long> {
}
