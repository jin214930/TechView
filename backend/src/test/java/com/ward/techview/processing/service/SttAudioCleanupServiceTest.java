package com.ward.techview.processing.service;

import com.ward.techview.processing.api.cleanup.FailedAudioCleanupTarget;
import com.ward.techview.processing.entity.SttProcessingJob;
import com.ward.techview.processing.repository.SttProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SttAudioCleanupServiceTest {

    private static final Long PROCESSING_JOB_ID = 100L;
    private static final String AUDIO_OBJECT_KEY = "interviews/10/answers/20/audio.webm";
    private static final OffsetDateTime FAILED_AT = OffsetDateTime.of(
            2026, 8, 5, 3, 0, 0, 0, ZoneOffset.UTC
    );

    @Mock
    private SttProcessingJobRepository repository;

    private SttAudioCleanupService service;

    @BeforeEach
    void setUp() {
        service = new SttAudioCleanupService(repository);
    }

    @Test
    @DisplayName("정리 예정 시각이 지난 실패 음성을 커서 다음부터 제한된 개수만 조회한다")
    void findsDueAudioCleanupTargets() {
        SttProcessingJob job = failedJob();
        OffsetDateTime now = FAILED_AT.plusHours(25);
        long cursor = 50L;
        when(repository.findDueAudioCleanupJobs(eq(now), eq(cursor), any(Pageable.class)))
                .thenReturn(List.of(job));

        List<FailedAudioCleanupTarget> result = service.findDueTargets(now, cursor, 100);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findDueAudioCleanupJobs(eq(now), eq(cursor), pageableCaptor.capture());
        assertAll(
                () -> assertEquals(1, result.size()),
                () -> assertEquals(PROCESSING_JOB_ID, result.getFirst().processingJobId()),
                () -> assertEquals(AUDIO_OBJECT_KEY, result.getFirst().audioObjectKey()),
                () -> assertEquals("verified-etag", result.getFirst().sourceObjectEtag()),
                () -> assertEquals(100, pageableCaptor.getValue().getPageSize())
        );
    }

    @Test
    @DisplayName("정리 대상 조회 개수가 0 이하면 거절한다")
    void rejectsInvalidCleanupTargetLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findDueTargets(FAILED_AT, 0L, 0)
        );

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("정리 대상 조회 커서가 음수면 거절한다")
    void rejectsNegativeCleanupTargetCursor() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findDueTargets(FAILED_AT, -1L, 100)
        );

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("실패 음성 삭제 완료 시각을 기록한다")
    void marksAudioAsDeleted() {
        SttProcessingJob job = failedJob();
        OffsetDateTime deletedAt = FAILED_AT.plusHours(25);
        when(repository.findByIdForUpdate(PROCESSING_JOB_ID)).thenReturn(Optional.of(job));

        boolean result = service.markAudioDeleted(PROCESSING_JOB_ID, deletedAt);

        assertAll(
                () -> assertTrue(result),
                () -> assertEquals(deletedAt, job.getAudioDeletedAt())
        );
    }

    @Test
    @DisplayName("존재하지 않는 STT 작업의 삭제 완료 요청은 실패 결과를 반환한다")
    void returnsFalseWhenCleanupJobDoesNotExist() {
        when(repository.findByIdForUpdate(PROCESSING_JOB_ID)).thenReturn(Optional.empty());

        boolean result = service.markAudioDeleted(
                PROCESSING_JOB_ID,
                FAILED_AT.plusHours(25)
        );

        assertFalse(result);
    }

    private static SttProcessingJob failedJob() {
        SttProcessingJob job = SttProcessingJob.create(
                10L,
                20L,
                AUDIO_OBJECT_KEY,
                "verified-etag",
                FAILED_AT.minusMinutes(1)
        );
        ReflectionTestUtils.setField(job, "id", PROCESSING_JOB_ID);
        job.claim(FAILED_AT.minusMinutes(1), Duration.ofMinutes(1));
        job.fail("INVALID_AUDIO", "지원하지 않는 음성 파일입니다.");
        job.scheduleAudioCleanup(FAILED_AT);
        return job;
    }
}
