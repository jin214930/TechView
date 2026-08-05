package com.ward.techview.processing.service;

import com.ward.techview.processing.entity.ProcessingJob;
import com.ward.techview.processing.entity.SttProcessingJob;
import com.ward.techview.processing.repository.ProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionProcessingCleanupServiceTest {
    private static final Long SESSION_ID = 10L;
    private static final OffsetDateTime NOW = OffsetDateTime.of(
            2026, 8, 5, 0, 0, 0, 0, ZoneOffset.UTC
    );

    @Mock
    private ProcessingJobRepository processingJobRepository;

    private SessionProcessingCleanupService service;

    @BeforeEach
    void setUp() {
        service = new SessionProcessingCleanupService(processingJobRepository);
    }

    @Test
    @DisplayName("현재 실행 중인 작업이 없으면 세션 삭제를 허용한다")
    void allowsDeletionWhenNoJobIsActivelyProcessing() {
        ProcessingJob queuedJob = job();
        ProcessingJob expiredProcessingJob = job();
        expiredProcessingJob.claim(NOW.minusMinutes(2), Duration.ofMinutes(1));
        when(processingJobRepository.findAllByInterviewSessionIdForUpdate(SESSION_ID))
                .thenReturn(List.of(queuedJob, expiredProcessingJob));

        boolean result = service.canDeleteSession(SESSION_ID, NOW);

        assertTrue(result);
        verify(processingJobRepository).findAllByInterviewSessionIdForUpdate(SESSION_ID);
    }

    @Test
    @DisplayName("처리 lease가 유효한 작업이 있으면 세션 삭제를 거절한다")
    void rejectsDeletionWhenJobIsActivelyProcessing() {
        ProcessingJob activeJob = job();
        activeJob.claim(NOW.minusSeconds(30), Duration.ofMinutes(1));
        when(processingJobRepository.findAllByInterviewSessionIdForUpdate(SESSION_ID))
                .thenReturn(List.of(activeJob));

        boolean result = service.canDeleteSession(SESSION_ID, NOW);

        assertFalse(result);
    }

    private static SttProcessingJob job() {
        return SttProcessingJob.create(
                SESSION_ID,
                20L,
                "interviews/10/answers/20/audio.webm",
                "verified-etag",
                NOW.minusDays(1)
        );
    }
}
