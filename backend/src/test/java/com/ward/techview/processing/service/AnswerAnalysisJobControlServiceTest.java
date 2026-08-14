package com.ward.techview.processing.service;

import com.ward.techview.processing.api.analysis.ClaimedAnswerAnalysisJob;
import com.ward.techview.processing.api.analysis.AnswerProcessingStatusChangedEvent;
import com.ward.techview.processing.config.ProcessingWorkerProperties;
import com.ward.techview.processing.entity.AnswerAnalysisProcessingJob;
import com.ward.techview.processing.entity.ProcessingJobStatus;
import com.ward.techview.processing.repository.AnswerAnalysisProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationEventPublisher;

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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerAnalysisJobControlServiceTest {

    private static final Long PROCESSING_JOB_ID = 100L;
    private static final Long SESSION_ID = 10L;
    private static final Long ANSWER_ID = 20L;
    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);

    @Mock
    private AnswerAnalysisProcessingJobRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AnswerAnalysisJobControlService service;

    @BeforeEach
    void setUp() {
        ProcessingWorkerProperties properties = new ProcessingWorkerProperties(
                LEASE_DURATION,
                2,
                20,
                Duration.ofSeconds(10),
                20
        );
        service = new AnswerAnalysisJobControlService(repository, properties, eventPublisher);
    }

    @Test
    @DisplayName("실행 가능한 답변 분석 작업을 선점하고 처리 정보를 반환한다")
    void claimsExecutableJob() {
        OffsetDateTime beforeClaim = OffsetDateTime.now(ZoneOffset.UTC);
        AnswerAnalysisProcessingJob job = createJob(beforeClaim.minusMinutes(1));
        when(repository.findByIdForUpdate(PROCESSING_JOB_ID))
                .thenReturn(Optional.of(job));

        Optional<ClaimedAnswerAnalysisJob> result = service.claim(PROCESSING_JOB_ID);

        OffsetDateTime afterClaim = OffsetDateTime.now(ZoneOffset.UTC);
        assertTrue(result.isPresent());
        ClaimedAnswerAnalysisJob claimedJob = result.orElseThrow();
        assertAll(
                () -> assertEquals(PROCESSING_JOB_ID, claimedJob.processingJobId()),
                () -> assertEquals(SESSION_ID, claimedJob.interviewSessionId()),
                () -> assertEquals(ANSWER_ID, claimedJob.answerId()),
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
        verify(repository).findByIdForUpdate(PROCESSING_JOB_ID);
    }

    @Test
    @DisplayName("답변 분석 작업이 없으면 선점 결과를 반환하지 않는다")
    void returnsEmptyWhenJobDoesNotExist() {
        when(repository.findByIdForUpdate(PROCESSING_JOB_ID))
                .thenReturn(Optional.empty());

        Optional<ClaimedAnswerAnalysisJob> result = service.claim(PROCESSING_JOB_ID);

        assertTrue(result.isEmpty());
        verify(repository).findByIdForUpdate(PROCESSING_JOB_ID);
    }

    @Test
    @DisplayName("실행 예정 시각이 도래하지 않은 답변 분석 작업은 선점하지 않는다")
    void returnsEmptyBeforeScheduledTime() {
        AnswerAnalysisProcessingJob job = createJob(
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1)
        );
        when(repository.findByIdForUpdate(PROCESSING_JOB_ID))
                .thenReturn(Optional.of(job));

        Optional<ClaimedAnswerAnalysisJob> result = service.claim(PROCESSING_JOB_ID);

        assertAll(
                () -> assertTrue(result.isEmpty()),
                () -> assertEquals(ProcessingJobStatus.QUEUED, job.getStatus()),
                () -> assertEquals(0, job.getAttemptCount()),
                () -> assertNull(job.getLeaseExpiresAt())
        );
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("현재 시도의 답변 분석 작업을 성공 상태로 변경한다")
    void succeedsCurrentAttempt() {
        AnswerAnalysisProcessingJob job = createClaimedJob();
        when(repository.findByIdForUpdate(PROCESSING_JOB_ID))
                .thenReturn(Optional.of(job));

        boolean result = service.succeed(PROCESSING_JOB_ID, 1);

        assertAll(
                () -> assertTrue(result),
                () -> assertEquals(ProcessingJobStatus.SUCCEEDED, job.getStatus()),
                () -> assertNull(job.getLeaseExpiresAt())
        );
        verify(eventPublisher).publishEvent(new AnswerProcessingStatusChangedEvent(SESSION_ID));
    }

    @Test
    @DisplayName("현재 시도의 답변 분석 작업을 재시도 대기 상태로 변경한다")
    void retriesCurrentAttempt() {
        OffsetDateTime nextAttemptAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5);
        AnswerAnalysisProcessingJob job = createClaimedJob();
        when(repository.findByIdForUpdate(PROCESSING_JOB_ID))
                .thenReturn(Optional.of(job));

        boolean result = service.retry(
                PROCESSING_JOB_ID,
                1,
                nextAttemptAt,
                "LLM_TIMEOUT",
                "LLM 응답 시간이 초과되었습니다."
        );

        assertAll(
                () -> assertTrue(result),
                () -> assertEquals(ProcessingJobStatus.RETRY_WAIT, job.getStatus()),
                () -> assertEquals(nextAttemptAt, job.getNextAttemptAt()),
                () -> assertNull(job.getLeaseExpiresAt()),
                () -> assertEquals("LLM_TIMEOUT", job.getErrorCode()),
                () -> assertEquals("LLM 응답 시간이 초과되었습니다.", job.getErrorMessage())
        );
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("현재 시도의 답변 분석 작업을 최종 실패 상태로 변경한다")
    void failsCurrentAttempt() {
        AnswerAnalysisProcessingJob job = createClaimedJob();
        when(repository.findByIdForUpdate(PROCESSING_JOB_ID))
                .thenReturn(Optional.of(job));

        boolean result = service.fail(
                PROCESSING_JOB_ID,
                1,
                "LLM_INVALID_RESPONSE",
                "LLM 구조화 응답이 올바르지 않습니다."
        );

        assertAll(
                () -> assertTrue(result),
                () -> assertEquals(ProcessingJobStatus.FAILED, job.getStatus()),
                () -> assertNull(job.getLeaseExpiresAt()),
                () -> assertEquals("LLM_INVALID_RESPONSE", job.getErrorCode()),
                () -> assertEquals("LLM 구조화 응답이 올바르지 않습니다.", job.getErrorMessage())
        );
        verify(eventPublisher).publishEvent(new AnswerProcessingStatusChangedEvent(SESSION_ID));
    }

    @Test
    @DisplayName("답변 분석 작업이 재시도 한도에 도달하면 실패 이벤트를 발행한다")
    void publishesFailureEventWhenRetryLimitIsReached() {
        AnswerAnalysisProcessingJob job = createThirdAttemptJob();
        when(repository.findByIdForUpdate(PROCESSING_JOB_ID))
                .thenReturn(Optional.of(job));

        boolean result = service.retry(
                PROCESSING_JOB_ID,
                3,
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5),
                "LLM_TIMEOUT",
                "LLM 응답 시간이 초과되었습니다."
        );

        assertAll(
                () -> assertTrue(result),
                () -> assertEquals(ProcessingJobStatus.FAILED, job.getStatus())
        );
        verify(eventPublisher).publishEvent(new AnswerProcessingStatusChangedEvent(SESSION_ID));
    }

    @Test
    @DisplayName("오래된 시도의 상태 변경 요청은 현재 답변 분석 작업을 변경하지 않는다")
    void ignoresStaleAttempt() {
        AnswerAnalysisProcessingJob job = createSecondAttemptJob();
        OffsetDateTime leaseExpiresAt = job.getLeaseExpiresAt();
        when(repository.findByIdForUpdate(PROCESSING_JOB_ID))
                .thenReturn(Optional.of(job));

        boolean succeedResult = service.succeed(PROCESSING_JOB_ID, 1);
        boolean retryResult = service.retry(
                PROCESSING_JOB_ID,
                1,
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5),
                "STALE_RETRY",
                "오래된 작업의 재시도 요청"
        );
        boolean failResult = service.fail(
                PROCESSING_JOB_ID,
                1,
                "STALE_FAILURE",
                "오래된 작업의 실패 요청"
        );

        assertAll(
                () -> assertFalse(succeedResult),
                () -> assertFalse(retryResult),
                () -> assertFalse(failResult),
                () -> assertEquals(ProcessingJobStatus.PROCESSING, job.getStatus()),
                () -> assertEquals(2, job.getAttemptCount()),
                () -> assertEquals(leaseExpiresAt, job.getLeaseExpiresAt()),
                () -> assertEquals("LLM_TIMEOUT", job.getErrorCode()),
                () -> assertEquals("첫 번째 실패", job.getErrorMessage())
        );
    }

    private static AnswerAnalysisProcessingJob createClaimedJob() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AnswerAnalysisProcessingJob job = createJob(now.minusMinutes(1));
        job.claim(now, LEASE_DURATION);
        return job;
    }

    private static AnswerAnalysisProcessingJob createSecondAttemptJob() {
        OffsetDateTime firstAttemptAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2);
        OffsetDateTime secondAttemptAt = firstAttemptAt.plusMinutes(1);
        AnswerAnalysisProcessingJob job = createJob(firstAttemptAt);
        job.claim(firstAttemptAt, LEASE_DURATION);
        job.retry(secondAttemptAt, "LLM_TIMEOUT", "첫 번째 실패");
        job.claim(secondAttemptAt, LEASE_DURATION);
        return job;
    }

    private static AnswerAnalysisProcessingJob createThirdAttemptJob() {
        OffsetDateTime firstAttemptAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(3);
        OffsetDateTime secondAttemptAt = firstAttemptAt.plusMinutes(1);
        OffsetDateTime thirdAttemptAt = secondAttemptAt.plusMinutes(1);
        AnswerAnalysisProcessingJob job = createJob(firstAttemptAt);
        job.claim(firstAttemptAt, LEASE_DURATION);
        job.retry(secondAttemptAt, "LLM_TIMEOUT", "첫 번째 실패");
        job.claim(secondAttemptAt, LEASE_DURATION);
        job.retry(thirdAttemptAt, "LLM_TIMEOUT", "두 번째 실패");
        job.claim(thirdAttemptAt, LEASE_DURATION);
        return job;
    }

    private static AnswerAnalysisProcessingJob createJob(OffsetDateTime queuedAt) {
        AnswerAnalysisProcessingJob job = AnswerAnalysisProcessingJob.create(
                SESSION_ID,
                ANSWER_ID,
                queuedAt
        );
        ReflectionTestUtils.setField(job, "id", PROCESSING_JOB_ID);
        return job;
    }
}
