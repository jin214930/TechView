package com.ward.techview.voice.service;

import com.ward.techview.processing.api.FailedAudioCleanupTarget;
import com.ward.techview.processing.api.SttAudioCleanupApi;
import com.ward.techview.voice.api.AudioStorage;
import com.ward.techview.voice.api.AudioStorageException;
import com.ward.techview.voice.api.AudioStorageFailure;
import com.ward.techview.voice.config.FailedAudioCleanupProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FailedAudioCleanupServiceTest {

    @Mock
    private SttAudioCleanupApi sttAudioCleanupApi;

    @Mock
    private AudioStorage audioStorage;

    private FailedAudioCleanupService service;

    @BeforeEach
    void setUp() {
        FailedAudioCleanupProperties properties =
                new FailedAudioCleanupProperties("0 0 4 * * *", "Asia/Seoul", 2);
        service = new FailedAudioCleanupService(
                sttAudioCleanupApi,
                audioStorage,
                properties
        );
    }

    @Test
    @DisplayName("커서를 이동하며 정리 대상을 배치 단위로 모두 삭제한다")
    void deletesAllTargetsInCursorBatches() {
        FailedAudioCleanupTarget first = target(1L);
        FailedAudioCleanupTarget second = target(2L);
        FailedAudioCleanupTarget third = target(3L);
        when(sttAudioCleanupApi.findDueTargets(any(), eq(0L), eq(2)))
                .thenReturn(List.of(first, second));
        when(sttAudioCleanupApi.findDueTargets(any(), eq(2L), eq(2)))
                .thenReturn(List.of(third));
        when(sttAudioCleanupApi.markAudioDeleted(eq(1L), any(OffsetDateTime.class)))
                .thenReturn(true);
        when(sttAudioCleanupApi.markAudioDeleted(eq(2L), any(OffsetDateTime.class)))
                .thenReturn(true);
        when(sttAudioCleanupApi.markAudioDeleted(eq(3L), any(OffsetDateTime.class)))
                .thenReturn(true);

        int deletedCount = service.cleanup();

        assertEquals(3, deletedCount);
        verify(audioStorage).deleteUnconditionally(first.audioObjectKey());
        verify(audioStorage).deleteUnconditionally(second.audioObjectKey());
        verify(audioStorage).deleteUnconditionally(third.audioObjectKey());
    }

    @Test
    @DisplayName("한 배치가 가득 차면 다음 배치가 없는지까지 조회한다")
    void queriesUntilAnEmptyBatchAfterFullBatch() {
        FailedAudioCleanupTarget first = target(1L);
        FailedAudioCleanupTarget second = target(2L);
        when(sttAudioCleanupApi.findDueTargets(any(), eq(0L), eq(2)))
                .thenReturn(List.of(first, second));
        when(sttAudioCleanupApi.findDueTargets(any(), eq(2L), eq(2)))
                .thenReturn(List.of());
        when(sttAudioCleanupApi.markAudioDeleted(eq(1L), any(OffsetDateTime.class)))
                .thenReturn(true);
        when(sttAudioCleanupApi.markAudioDeleted(eq(2L), any(OffsetDateTime.class)))
                .thenReturn(true);

        int deletedCount = service.cleanup();

        assertEquals(2, deletedCount);
        verify(sttAudioCleanupApi).findDueTargets(any(), eq(2L), eq(2));
    }

    @Test
    @DisplayName("한 객체 삭제가 실패해도 나머지 대상은 계속 정리한다")
    void continuesWhenOneStorageDeletionFails() {
        FailedAudioCleanupTarget first = target(1L);
        FailedAudioCleanupTarget second = target(2L);
        when(sttAudioCleanupApi.findDueTargets(any(), eq(0L), eq(2)))
                .thenReturn(List.of(first, second));
        when(sttAudioCleanupApi.findDueTargets(any(), eq(2L), eq(2)))
                .thenReturn(List.of());
        doThrow(new AudioStorageException(
                AudioStorageFailure.TEMPORARY_FAILURE,
                "일시적 오류",
                new RuntimeException()
        )).when(audioStorage).deleteUnconditionally(first.audioObjectKey());
        when(sttAudioCleanupApi.markAudioDeleted(eq(2L), any(OffsetDateTime.class)))
                .thenReturn(true);

        int deletedCount = service.cleanup();

        assertEquals(1, deletedCount);
        verify(sttAudioCleanupApi, never())
                .markAudioDeleted(eq(1L), any(OffsetDateTime.class));
        verify(audioStorage).deleteUnconditionally(second.audioObjectKey());
    }

    @Test
    @DisplayName("삭제 완료 상태를 반영하지 못하면 삭제 건수에서 제외한다")
    void excludesDeletionWhenCompletionMarkFails() {
        FailedAudioCleanupTarget target = target(1L);
        when(sttAudioCleanupApi.findDueTargets(any(), eq(0L), eq(2)))
                .thenReturn(List.of(target));
        when(sttAudioCleanupApi.markAudioDeleted(eq(1L), any(OffsetDateTime.class)))
                .thenReturn(false);

        int deletedCount = service.cleanup();

        assertEquals(0, deletedCount);
    }

    private static FailedAudioCleanupTarget target(Long processingJobId) {
        return new FailedAudioCleanupTarget(
                processingJobId,
                "interviews/10/answers/" + processingJobId + "/audio.webm"
        );
    }
}
