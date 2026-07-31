package com.ward.techview.interview.repository;

import com.ward.techview.interview.entity.InterviewSession;
import com.ward.techview.interview.entity.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
    @Query("""
            SELECT session
            FROM InterviewSession session
            WHERE session.memberId = :memberId
              AND session.status = :status
              AND session.startedAt >= :cutoff
            """)
    Optional<InterviewSession> findActiveSession(Long memberId, InterviewStatus status, OffsetDateTime cutoff);
}
