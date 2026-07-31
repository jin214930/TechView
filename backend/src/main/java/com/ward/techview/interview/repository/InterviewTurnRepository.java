package com.ward.techview.interview.repository;

import com.ward.techview.interview.entity.InterviewTurn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InterviewTurnRepository extends JpaRepository<InterviewTurn, Long> {
    @Query("""
            SELECT turn
            FROM InterviewTurn turn
            LEFT JOIN FETCH turn.interviewCoreQuestion
            LEFT JOIN FETCH turn.followUpQuestion
            WHERE turn.session.id = :sessionId AND turn.turnOrder = :turnOrder
            """)
    Optional<InterviewTurn> findCurrentTurn(@Param("sessionId") Long sessionId, @Param("turnOrder") int turnOrder);

    Optional<InterviewTurn> findByIdAndSessionId(Long turnId, Long sessionId);
}
