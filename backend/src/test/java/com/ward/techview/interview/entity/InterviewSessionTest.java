package com.ward.techview.interview.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewSessionTest {
    private static final OffsetDateTime CUTOFF = OffsetDateTime.of(
            2026, 8, 5, 0, 0, 0, 0, ZoneOffset.UTC
    );

    @Test
    @DisplayName("진행 중인 세션의 시작 시각이 기준보다 이전이면 만료로 판단한다")
    void considersOldInProgressSessionExpired() {
        InterviewSession session = InterviewSession.create(1L, CUTOFF.minusNanos(1));

        assertTrue(session.isExpired(CUTOFF));
    }

    @Test
    @DisplayName("시작 시각이 만료 기준과 같으면 만료되지 않은 것으로 판단한다")
    void doesNotExpireSessionAtExactCutoff() {
        InterviewSession session = InterviewSession.create(1L, CUTOFF);

        assertFalse(session.isExpired(CUTOFF));
    }

    @Test
    @DisplayName("완료된 세션은 시작 시각이 오래되어도 만료 정리 대상으로 판단하지 않는다")
    void doesNotExpireCompletedSession() {
        InterviewSession session = InterviewSession.create(1L, CUTOFF.minusDays(1));
        session.complete(CUTOFF.minusHours(1));

        assertFalse(session.isExpired(CUTOFF));
    }

    @Test
    @DisplayName("만료 기준 시각이 없으면 만료 판정을 거절한다")
    void rejectsMissingExpirationCutoff() {
        InterviewSession session = InterviewSession.create(1L, CUTOFF.minusDays(1));

        assertThrows(NullPointerException.class, () -> session.isExpired(null));
    }
}
