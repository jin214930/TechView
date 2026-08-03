package com.ward.techview.processing.service;

import com.ward.techview.processing.api.ClaimedSttJob;
import com.ward.techview.processing.config.ProcessingWorkerProperties;
import com.ward.techview.processing.entity.ProcessingJobStatus;
import com.ward.techview.processing.entity.SttProcessingJob;
import com.ward.techview.processing.repository.SttProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SttJobControlServiceTest {

    private static final Long PROCESSING_JOB_ID = 100L;
    private static final Long SESSION_ID = 10L;
    private static final Long ANSWER_ID = 20L;
    private static final String AUDIO_OBJECT_KEY = "interviews/10/answers/20/audio.webm";
    private static final String SOURCE_OBJECT_ETAG = "verified-etag";
    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);

    @Mock
    private SttProcessingJobRepository sttProcessingJobRepository;

    private SttJobControlService sttJobControlService;

    @BeforeEach
    void setUp() {
        ProcessingWorkerProperties properties = new ProcessingWorkerProperties(
                LEASE_DURATION,
                2,
                20
        );
        sttJobControlService = new SttJobControlService(
                sttProcessingJobRepository,
                properties
        );
    }

    @Test
    @DisplayName("실행 가능한 STT 작업을 선점하고 처리 정보를 반환한다")
    void claimsExecutableSttJob() {
        OffsetDateTime beforeClaim = OffsetDateTime.now(ZoneOffset.UTC);
        SttProcessingJob job = createJob(beforeClaim.minusMinutes(1));
        when(sttProcessingJobRepository.findByIdForUpdate(PROCESSING_JOB_ID))
                .thenReturn(Optional.of(job));

        Optional<ClaimedSttJob> result = sttJobControlService.claim(PROCESSING_JOB_ID);

        OffsetDateTime afterClaim = OffsetDateTime.now(ZoneOffset.UTC);
        assertTrue(result.isPresent());
        ClaimedSttJob claimedJob = result.get();
        assertAll(
                () -> assertEquals(PROCESSING_JOB_ID, claimedJob.processingJobId()),
                () -> assertEquals(SESSION_ID, claimedJob.interviewSessionId()),
                () -> assertEquals(ANSWER_ID, claimedJob.answerId()),
                () -> assertEquals(AUDIO_OBJECT_KEY, claimedJob.audioObjectKey()),
                () -> assertEquals(SOURCE_OBJECT_ETAG, claimedJob.sourceObjectEtag()),
                () -> assertEquals(1, claimedJob.attemptCount()),
                () -> assertEquals(ProcessingJobStatus.PROCESSING, job.getStatus()),
                () -> assertEquals(1, job.getAttemptCount()),
                () -> assertFalse(
                        job.getLeaseExpiresAt().isBefore(beforeClaim.plus(LEASE_DURATION))
                ),
                () -> assertFalse(
                        job.getLeaseExpiresAt().isAfter(afterClaim.plus(LEASE_DURATION))
                )
        );
        verify(sttProcessingJobRepository).findByIdForUpdate(PROCESSING_JOB_ID);
    }

    @Test
    @DisplayName("STT 작업이 존재하지 않으면 선점 결과를 반환하지 않는다")
    void returnsEmptyWhenJobDoesNotExist() {
        when(sttProcessingJobRepository.findByIdForUpdate(PROCESSING_JOB_ID))
                .thenReturn(Optional.empty());

        Optional<ClaimedSttJob> result = sttJobControlService.claim(PROCESSING_JOB_ID);

        assertTrue(result.isEmpty());
        verify(sttProcessingJobRepository).findByIdForUpdate(PROCESSING_JOB_ID);
    }

    @Test
    @DisplayName("실행 예정 시각이 도래하지 않은 STT 작업은 선점하지 않는다")
    void returnsEmptyBeforeScheduledTime() {
        SttProcessingJob job = createJob(
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1)
        );
        when(sttProcessingJobRepository.findByIdForUpdate(PROCESSING_JOB_ID))
                .thenReturn(Optional.of(job));

        Optional<ClaimedSttJob> result = sttJobControlService.claim(PROCESSING_JOB_ID);

        assertAll(
                () -> assertTrue(result.isEmpty()),
                () -> assertEquals(ProcessingJobStatus.QUEUED, job.getStatus()),
                () -> assertEquals(0, job.getAttemptCount()),
                () -> assertFalse(job.isClaimable(OffsetDateTime.now(ZoneOffset.UTC)))
        );
    }

    @Test
    @DisplayName("유효한 lease로 처리 중인 STT 작업은 다시 선점하지 않는다")
    void returnsEmptyWhileLeaseIsActive() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        SttProcessingJob job = createJob(now.minusMinutes(1));
        job.claim(now, LEASE_DURATION);
        OffsetDateTime leaseExpiresAt = job.getLeaseExpiresAt();
        when(sttProcessingJobRepository.findByIdForUpdate(PROCESSING_JOB_ID))
                .thenReturn(Optional.of(job));

        Optional<ClaimedSttJob> result = sttJobControlService.claim(PROCESSING_JOB_ID);

        assertAll(
                () -> assertTrue(result.isEmpty()),
                () -> assertEquals(ProcessingJobStatus.PROCESSING, job.getStatus()),
                () -> assertEquals(1, job.getAttemptCount()),
                () -> assertEquals(leaseExpiresAt, job.getLeaseExpiresAt())
        );
    }

    @Test
    @DisplayName("현재 시도의 STT 작업을 성공 상태로 변경한다")
    void succeedsCurrentAttempt() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        SttProcessingJob job = createJob(now.minusMinutes(1));
        job.claim(now, LEASE_DURATION);
        when(sttProcessingJobRepository.findByIdForUpdate(PROCESSING_JOB_ID))
                .thenReturn(Optional.of(job));

        boolean result = sttJobControlService.succeed(PROCESSING_JOB_ID, 1);

        assertAll(
                () -> assertTrue(result),
                () -> assertEquals(ProcessingJobStatus.SUCCEEDED, job.getStatus()),
                () -> assertNull(job.getLeaseExpiresAt())
        );
        verify(sttProcessingJobRepository).findByIdForUpdate(PROCESSING_JOB_ID);
    }

    @Test
    @DisplayName("현재 시도의 STT 작업을 재시도 대기 상태로 변경한다")
    void retriesCurrentAttempt() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime nextAttemptAt = now.plusMinutes(5);
        SttProcessingJob job = createJob(now.minusMinutes(1));
        job.claim(now, LEASE_DURATION);
        when(sttProcessingJobRepository.findByIdForUpdate(PROCESSING_JOB_ID))
                .thenReturn(Optional.of(job));

        boolean result = sttJobControlService.retry(
                PROCESSING_JOB_ID,
                1,
                nextAttemptAt,
                "STT_TIMEOUT",
                "STT 요청 시간이 초과되었습니다."
        );

        assertAll(
                () -> assertTrue(result),
                () -> assertEquals(ProcessingJobStatus.RETRY_WAIT, job.getStatus()),
                () -> assertEquals(nextAttemptAt, job.getNextAttemptAt()),
                () -> assertNull(job.getLeaseExpiresAt()),
                () -> assertEquals("STT_TIMEOUT", job.getErrorCode()),
                () -> assertEquals("STT 요청 시간이 초과되었습니다.", job.getErrorMessage())
        );
    }

    @Test
    @DisplayName("현재 시도의 STT 작업을 최종 실패 상태로 변경한다")
    void failsCurrentAttempt() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        SttProcessingJob job = createJob(now.minusMinutes(1));
        job.claim(now, LEASE_DURATION);
        when(sttProcessingJobRepository.findByIdForUpdate(PROCESSING_JOB_ID))
                .thenReturn(Optional.of(job));

        boolean result = sttJobControlService.fail(
                PROCESSING_JOB_ID,
                1,
                "INVALID_AUDIO",
                "지원하지 않는 음성 파일입니다."
        );

        assertAll(
                () -> assertTrue(result),
                () -> assertEquals(ProcessingJobStatus.FAILED, job.getStatus()),
                () -> assertNull(job.getLeaseExpiresAt()),
                () -> assertEquals("INVALID_AUDIO", job.getErrorCode()),
                () -> assertEquals("지원하지 않는 음성 파일입니다.", job.getErrorMessage())
        );
    }

    @Test
    @DisplayName("오래된 시도의 성공 요청은 현재 작업 상태를 변경하지 않는다")
    void ignoresStaleSuccessAttempt() {
        SttProcessingJob job = createSecondAttemptJob();
        OffsetDateTime leaseExpiresAt = job.getLeaseExpiresAt();
        when(sttProcessingJobRepository.findByIdForUpdate(PROCESSING_JOB_ID))
                .thenReturn(Optional.of(job));

        boolean result = sttJobControlService.succeed(PROCESSING_JOB_ID, 1);

        assertAll(
                () -> assertFalse(result),
                () -> assertEquals(ProcessingJobStatus.PROCESSING, job.getStatus()),
                () -> assertEquals(2, job.getAttemptCount()),
                () -> assertEquals(leaseExpiresAt, job.getLeaseExpiresAt())
        );
    }

    @Test
    @DisplayName("오래된 시도의 재시도 요청은 현재 작업 상태를 변경하지 않는다")
    void ignoresStaleRetryAttempt() {
        SttProcessingJob job = createSecondAttemptJob();
        OffsetDateTime leaseExpiresAt = job.getLeaseExpiresAt();
        when(sttProcessingJobRepository.findByIdForUpdate(PROCESSING_JOB_ID))
                .thenReturn(Optional.of(job));

        boolean result = sttJobControlService.retry(
                PROCESSING_JOB_ID,
                1,
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5),
                "STT_TIMEOUT",
                "오래된 작업의 실패"
        );

        assertAll(
                () -> assertFalse(result),
                () -> assertEquals(ProcessingJobStatus.PROCESSING, job.getStatus()),
                () -> assertEquals(2, job.getAttemptCount()),
                () -> assertEquals(leaseExpiresAt, job.getLeaseExpiresAt()),
                () -> assertEquals("STT_TIMEOUT", job.getErrorCode()),
                () -> assertEquals("첫 번째 실패", job.getErrorMessage())
        );
    }

    @Test
    @DisplayName("존재하지 않는 STT 작업의 상태 변경 요청은 실패 결과를 반환한다")
    void returnsFalseWhenUpdatingMissingJob() {
        when(sttProcessingJobRepository.findByIdForUpdate(PROCESSING_JOB_ID))
                .thenReturn(Optional.empty());

        boolean result = sttJobControlService.fail(
                PROCESSING_JOB_ID,
                1,
                "STT_ERROR",
                "STT 처리 실패"
        );

        assertFalse(result);
        verify(sttProcessingJobRepository).findByIdForUpdate(PROCESSING_JOB_ID);
    }

    private static SttProcessingJob createSecondAttemptJob() {
        OffsetDateTime firstAttemptAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2);
        OffsetDateTime secondAttemptAt = firstAttemptAt.plusMinutes(1);
        SttProcessingJob job = createJob(firstAttemptAt);
        job.claim(firstAttemptAt, LEASE_DURATION);
        job.retry(secondAttemptAt, "STT_TIMEOUT", "첫 번째 실패");
        job.claim(secondAttemptAt, LEASE_DURATION);
        return job;
    }

    private static SttProcessingJob createJob(OffsetDateTime queuedAt) {
        SttProcessingJob job = SttProcessingJob.create(
                SESSION_ID,
                ANSWER_ID,
                AUDIO_OBJECT_KEY,
                SOURCE_OBJECT_ETAG,
                queuedAt
        );
        ReflectionTestUtils.setField(job, "id", PROCESSING_JOB_ID);
        return job;
    }
}
