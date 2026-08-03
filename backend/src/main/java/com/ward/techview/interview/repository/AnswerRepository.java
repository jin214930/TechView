package com.ward.techview.interview.repository;

import com.ward.techview.interview.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    Optional<Answer> findByInterviewTurnId(Long interviewTurnId);

    @Query("""
            SELECT answer
            FROM Answer answer
            JOIN FETCH answer.interviewTurn turn
            JOIN FETCH turn.session session
            WHERE answer.id = :answerId
              AND session.memberId = :memberId
            """)
    Optional<Answer> findByIdAndMemberId(@Param("answerId") Long answerId, @Param("memberId") Long memberId);
}
