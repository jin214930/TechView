package com.ward.techview.evaluation.repository;

import com.ward.techview.evaluation.entity.InterviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;

public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, Long> {
    Optional<InterviewFeedback> findByInterviewSessionId(Long interviewSessionId);

    @Query("""
            SELECT feedback.interviewSessionId
            FROM InterviewFeedback feedback
            WHERE feedback.interviewSessionId IN :interviewSessionIds
            """)
    Set<Long> findExistingInterviewSessionIds(@Param("interviewSessionIds") Set<Long> interviewSessionIds);
}
