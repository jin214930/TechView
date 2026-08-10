package com.ward.techview.interview.repository;

import com.ward.techview.interview.entity.InterviewSession;
import com.ward.techview.interview.entity.InterviewStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT session
            FROM InterviewSession session
            WHERE session.id = :sessionId
              AND session.memberId = :memberId
            """)
    Optional<InterviewSession> findByIdAndMemberIdForUpdate(@Param("sessionId") Long sessionId, @Param("memberId") Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT session
            FROM InterviewSession session
            WHERE session.id = :sessionId
            """)
    Optional<InterviewSession> findByIdForUpdate(@Param("sessionId") Long sessionId);

    Optional<InterviewSession> findByIdAndMemberId(Long sessionId, Long memberId);

    boolean existsByMemberIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            Long memberId,
            OffsetDateTime startOfDay,
            OffsetDateTime startOfNextDay
    );

    Page<InterviewSession> findAllByMemberIdAndStatus(Long memberId, InterviewStatus status, Pageable pageable);

    @Query(
            value = """
                    SELECT session.*
                    FROM interview_session session
                    WHERE session.status = 'IN_PROGRESS'
                      AND session.started_at < :cutoff
                      AND session.id > :afterSessionId
                    ORDER BY session.id
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<InterviewSession> findExpiredSessionsForUpdateSkipLocked(
            @Param("cutoff") OffsetDateTime cutoff,
            @Param("afterSessionId") Long afterSessionId,
            @Param("limit") int limit
    );
}
