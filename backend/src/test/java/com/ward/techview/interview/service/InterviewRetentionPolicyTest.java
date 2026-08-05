package com.ward.techview.interview.service;

import com.ward.techview.interview.config.ExpiredInterviewCleanupProperties;
import com.ward.techview.interview.entity.InterviewSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewRetentionPolicyTest {
    private static final OffsetDateTime NOW = OffsetDateTime.of(
            2026, 8, 5, 0, 0, 0, 0, ZoneOffset.UTC
    );

    private InterviewRetentionPolicy policy;

    @BeforeEach
    void setUp() {
        ExpiredInterviewCleanupProperties properties = new ExpiredInterviewCleanupProperties(
                Duration.ofDays(30),
                "0 30 4 * * *",
                "Asia/Seoul",
                100
        );
        policy = new InterviewRetentionPolicy(properties);
    }

    @Test
    @DisplayName("설정된 보관 기간으로 만료 기준 시각을 계산한다")
    void calculatesCutoffFromRetentionPeriod() {
        assertEquals(NOW.minusDays(30), policy.calculateCutoff(NOW));
    }

    @Test
    @DisplayName("보관 기간보다 오래된 진행 세션을 만료로 판단한다")
    void identifiesExpiredSession() {
        InterviewSession expired = InterviewSession.create(1L, NOW.minusDays(30).minusNanos(1));
        InterviewSession active = InterviewSession.create(1L, NOW.minusDays(30));

        assertTrue(policy.isExpired(expired, NOW));
        assertFalse(policy.isExpired(active, NOW));
    }

    @Test
    @DisplayName("현재 시각이나 면접 세션이 없으면 만료 정책 적용을 거절한다")
    void rejectsMissingArguments() {
        InterviewSession session = InterviewSession.create(1L, NOW.minusDays(1));

        assertThrows(NullPointerException.class, () -> policy.calculateCutoff(null));
        assertThrows(NullPointerException.class, () -> policy.isExpired(null, NOW));
        assertThrows(NullPointerException.class, () -> policy.isExpired(session, null));
    }
}
