package com.ward.techview.interview.repository;

import com.ward.techview.interview.entity.Answer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    @Query("""
            SELECT answer
            FROM Answer answer
            JOIN FETCH answer.interviewTurn turn
            JOIN FETCH turn.session session
            WHERE answer.id = :answerId
              AND session.id = :sessionId
            """)
    Optional<Answer> findByIdAndSessionId(@Param("answerId") Long answerId, @Param("sessionId") Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT answer
            FROM Answer answer
            JOIN answer.interviewTurn turn
            JOIN turn.session session
            WHERE answer.id = :answerId
              AND session.id = :interviewSessionId
            """)
    Optional<Answer> findByIdAndSessionIdForUpdate(@Param("answerId") Long answerId, @Param("interviewSessionId") Long interviewSessionId);

    @Query("""
            SELECT answer
            FROM Answer answer
            JOIN FETCH answer.interviewTurn turn
            JOIN FETCH turn.session session
            LEFT JOIN FETCH turn.interviewCoreQuestion coreQuestion
            LEFT JOIN FETCH turn.followUpQuestion followUpQuestion
            LEFT JOIN FETCH followUpQuestion.coreQuestion followUpCoreQuestion
            WHERE answer.id = :answerId
              AND session.id = :sessionId
            """)
    Optional<Answer> findAnalysisTarget(@Param("sessionId") Long sessionId, @Param("answerId") Long answerId);

    @Query("""
            SELECT answer
            FROM Answer answer
            JOIN FETCH answer.interviewTurn turn
            JOIN FETCH turn.session session
            LEFT JOIN FETCH turn.interviewCoreQuestion coreQuestion
            LEFT JOIN FETCH turn.followUpQuestion followUpQuestion
            LEFT JOIN FETCH followUpQuestion.coreQuestion followUpCoreQuestion
            WHERE session.id = :interviewSessionId
            ORDER BY turn.turnOrder
            """)
    List<Answer> findAllFeedbackTargets(@Param("interviewSessionId") Long interviewSessionId);
}
