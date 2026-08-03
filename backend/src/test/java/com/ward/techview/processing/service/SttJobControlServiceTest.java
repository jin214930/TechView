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
        ProcessingWorkerProperties properties = new ProcessingWorkerProperties(LEASE_DURATION);
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
