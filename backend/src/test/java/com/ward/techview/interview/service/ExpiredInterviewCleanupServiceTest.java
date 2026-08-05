package com.ward.techview.interview.service;

import com.ward.techview.interview.config.ExpiredInterviewCleanupProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiredInterviewCleanupServiceTest {
    private static final int BATCH_SIZE = 2;
    private static final OffsetDateTime CUTOFF = OffsetDateTime.of(
            2026, 7, 6, 0, 0, 0, 0, ZoneOffset.UTC
    );

    @Mock
    private ExpiredInterviewCleanupBatchService batchService;

    @Mock
    private InterviewRetentionPolicy retentionPolicy;

    private ExpiredInterviewCleanupService service;

    @BeforeEach
    void setUp() {
        ExpiredInterviewCleanupProperties properties = new ExpiredInterviewCleanupProperties(
                Duration.ofDays(30),
                "0 30 4 * * *",
                "Asia/Seoul",
                BATCH_SIZE
        );
        service = new ExpiredInterviewCleanupService(
                batchService,
                properties,
                retentionPolicy
        );
    }

    @Test
    @DisplayName("마지막 부분 배치까지 커서를 이동하며 만료 세션을 정리한다")
    void cleansAllCursorBatches() {
        when(retentionPolicy.calculateCutoff(any(OffsetDateTime.class)))
                .thenReturn(CUTOFF);
        when(batchService.cleanupBatch(any(), eq(CUTOFF), eq(0L), eq(BATCH_SIZE)))
                .thenReturn(new ExpiredInterviewCleanupBatchResult(20L, 2, 1));
        when(batchService.cleanupBatch(any(), eq(CUTOFF), eq(20L), eq(BATCH_SIZE)))
                .thenReturn(new ExpiredInterviewCleanupBatchResult(30L, 1, 1));

        int deletedCount = service.cleanup();

        assertEquals(2, deletedCount);
        InOrder order = inOrder(batchService);
        order.verify(batchService).cleanupBatch(any(), eq(CUTOFF), eq(0L), eq(BATCH_SIZE));
        order.verify(batchService).cleanupBatch(any(), eq(CUTOFF), eq(20L), eq(BATCH_SIZE));
    }

    @Test
    @DisplayName("가득 찬 마지막 배치 뒤 빈 배치까지 조회하고 종료한다")
    void stopsAfterEmptyBatchFollowingFullBatch() {
        when(retentionPolicy.calculateCutoff(any(OffsetDateTime.class)))
                .thenReturn(CUTOFF);
        when(batchService.cleanupBatch(any(), eq(CUTOFF), eq(0L), eq(BATCH_SIZE)))
                .thenReturn(new ExpiredInterviewCleanupBatchResult(20L, 2, 2));
        when(batchService.cleanupBatch(any(), eq(CUTOFF), eq(20L), eq(BATCH_SIZE)))
                .thenReturn(ExpiredInterviewCleanupBatchResult.empty(20L));

        int deletedCount = service.cleanup();

        assertEquals(2, deletedCount);
        verify(batchService).cleanupBatch(any(), eq(CUTOFF), eq(20L), eq(BATCH_SIZE));
    }
}
