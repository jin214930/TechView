package com.ward.techview.interview.service;

import com.ward.techview.interview.entity.InterviewSession;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import com.ward.techview.processing.api.cleanup.SessionProcessingCleanupApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiredInterviewCleanupBatchServiceTest {
    private static final OffsetDateTime NOW = OffsetDateTime.of(
            2026, 8, 5, 0, 0, 0, 0, ZoneOffset.UTC
    );
    private static final OffsetDateTime CUTOFF = NOW.minusDays(30);

    @Mock
    private InterviewSessionRepository interviewSessionRepository;

    @Mock
    private SessionProcessingCleanupApi sessionProcessingCleanupApi;

    private ExpiredInterviewCleanupBatchService service;

    @BeforeEach
    void setUp() {
        service = new ExpiredInterviewCleanupBatchService(
                interviewSessionRepository,
                sessionProcessingCleanupApi
        );
    }

    @Test
    @DisplayName("실행 중인 작업이 없는 만료 세션만 삭제하고 마지막 조회 ID를 반환한다")
    void deletesOnlyCleanupSafeSessionsAndAdvancesCursor() {
        InterviewSession deletable = expiredSession(10L);
        InterviewSession active = expiredSession(20L);
        when(interviewSessionRepository.findExpiredSessionsForUpdateSkipLocked(
                CUTOFF,
                0L,
                100
        )).thenReturn(List.of(deletable, active));
        when(sessionProcessingCleanupApi.canDeleteSession(10L, NOW)).thenReturn(true);
        when(sessionProcessingCleanupApi.canDeleteSession(20L, NOW)).thenReturn(false);

        ExpiredInterviewCleanupBatchResult result = service.cleanupBatch(
                NOW,
                CUTOFF,
                0L,
                100
        );

        assertAll(
                () -> assertEquals(20L, result.nextCursor()),
                () -> assertEquals(2, result.scannedCount()),
                () -> assertEquals(1, result.deletedCount())
        );
        verify(interviewSessionRepository).delete(deletable);
        verify(interviewSessionRepository, never()).delete(active);
    }

    @Test
    @DisplayName("정리 대상이 없으면 현재 커서와 0건 결과를 반환한다")
    void returnsEmptyResultWhenNoSessionExists() {
        when(interviewSessionRepository.findExpiredSessionsForUpdateSkipLocked(
                CUTOFF,
                50L,
                100
        )).thenReturn(List.of());

        ExpiredInterviewCleanupBatchResult result = service.cleanupBatch(
                NOW,
                CUTOFF,
                50L,
                100
        );

        assertAll(
                () -> assertEquals(50L, result.nextCursor()),
                () -> assertEquals(0, result.scannedCount()),
                () -> assertEquals(0, result.deletedCount())
        );
        verify(sessionProcessingCleanupApi, never())
                .canDeleteSession(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("음수 커서나 0 이하 배치 크기를 거절한다")
    void rejectsInvalidPagingArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.cleanupBatch(NOW, CUTOFF, -1L, 100)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.cleanupBatch(NOW, CUTOFF, 0L, 0)
        );
    }

    private static InterviewSession expiredSession(Long id) {
        InterviewSession session = InterviewSession.create(1L, CUTOFF.minusDays(1));
        ReflectionTestUtils.setField(session, "id", id);
        return session;
    }
}
