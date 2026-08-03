package com.ward.techview.processing.service;

import com.ward.techview.processing.api.AnswerAnalysisJobQueuedEvent;
import com.ward.techview.processing.api.FinalFeedbackJobQueuedEvent;
import com.ward.techview.processing.api.SttTranscriptionJobQueuedEvent;
import com.ward.techview.processing.config.ProcessingWorkerProperties;
import com.ward.techview.processing.entity.AnswerAnalysisProcessingJob;
import com.ward.techview.processing.entity.FinalFeedbackProcessingJob;
import com.ward.techview.processing.entity.ProcessingJob;
import com.ward.techview.processing.entity.ProcessingJobStatus;
import com.ward.techview.processing.entity.SttProcessingJob;
import com.ward.techview.processing.repository.ProcessingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessingJobPollingServiceTest {

    private static final int BATCH_SIZE = 20;

    @Mock
    private ProcessingJobRepository processingJobRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ProcessingJobPollingService pollingService;

    @BeforeEach
    void setUp() {
        ProcessingWorkerProperties properties = new ProcessingWorkerProperties(
                Duration.ofMinutes(2),
                2,
                20,
                Duration.ofSeconds(10),
                BATCH_SIZE
        );
        pollingService = new ProcessingJobPollingService(
                processingJobRepository,
                properties,
                eventPublisher
        );
    }

    @Test
    @DisplayName("실행 가능한 작업을 유형별 이벤트로 발행한다")
    void publishesDispatchEventForEveryJobType() {
        ProcessingJob sttJob = sttJob(100L);
        ProcessingJob analysisJob = answerAnalysisJob(200L);
        ProcessingJob feedbackJob = finalFeedbackJob(300L);
        when(processingJobRepository.findExhaustedExpiredJobs(
                eq(ProcessingJobStatus.PROCESSING),
                any(OffsetDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of());
        when(processingJobRepository.findDispatchableJobs(
                eq(List.of(
                        ProcessingJobStatus.QUEUED,
                        ProcessingJobStatus.RETRY_WAIT
                )),
                eq(ProcessingJobStatus.PROCESSING),
                any(OffsetDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(sttJob, analysisJob, feedbackJob));

        int dispatchedCount = pollingService.pollAndPublish();

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.times(3))
                .publishEvent(eventCaptor.capture());
        List<Object> events = eventCaptor.getAllValues();
        assertAll(
                () -> assertEquals(3, dispatchedCount),
                () -> assertEquals(
                        100L,
                        assertInstanceOf(
                                SttTranscriptionJobQueuedEvent.class,
                                events.get(0)
                        ).processingJobId()
                ),
                () -> assertEquals(
                        200L,
                        assertInstanceOf(
                                AnswerAnalysisJobQueuedEvent.class,
                                events.get(1)
                        ).processingJobId()
                ),
                () -> assertEquals(
                        300L,
                        assertInstanceOf(
                                FinalFeedbackJobQueuedEvent.class,
                                events.get(2)
                        ).processingJobId()
                )
        );
    }

    @Test
    @DisplayName("최대 시도 횟수에서 lease가 만료된 작업을 최종 실패 처리한다")
    void failsExhaustedExpiredJob() {
        ProcessingJob exhaustedJob = exhaustedJob(400L);
        when(processingJobRepository.findExhaustedExpiredJobs(
                eq(ProcessingJobStatus.PROCESSING),
                any(OffsetDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(exhaustedJob));
        when(processingJobRepository.findDispatchableJobs(
                any(),
                eq(ProcessingJobStatus.PROCESSING),
                any(OffsetDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of());

        int dispatchedCount = pollingService.pollAndPublish();

        assertAll(
                () -> assertEquals(0, dispatchedCount),
                () -> assertEquals(
                        ProcessingJobStatus.FAILED,
                        exhaustedJob.getStatus()
                ),
                () -> assertEquals(
                        "WORKER_LEASE_EXPIRED",
                        exhaustedJob.getErrorCode()
                )
        );
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    private static SttProcessingJob sttJob(Long id) {
        SttProcessingJob job = SttProcessingJob.create(
                10L,
                20L,
                "interviews/10/answers/20/audio.webm",
                "verified-etag",
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1)
        );
        setId(job, id);
        return job;
    }

    private static AnswerAnalysisProcessingJob answerAnalysisJob(Long id) {
        AnswerAnalysisProcessingJob job = AnswerAnalysisProcessingJob.create(
                10L,
                20L,
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1)
        );
        setId(job, id);
        return job;
    }

    private static FinalFeedbackProcessingJob finalFeedbackJob(Long id) {
        FinalFeedbackProcessingJob job = FinalFeedbackProcessingJob.create(
                10L,
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1)
        );
        setId(job, id);
        return job;
    }

    private static ProcessingJob exhaustedJob(Long id) {
        OffsetDateTime firstAttemptAt =
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10);
        SttProcessingJob job = SttProcessingJob.create(
                10L,
                20L,
                "interviews/10/answers/20/audio.webm",
                "verified-etag",
                firstAttemptAt
        );
        job.claim(firstAttemptAt, Duration.ofSeconds(1));
        job.retry(
                firstAttemptAt.plusMinutes(1),
                "TEMPORARY_FAILURE",
                "일시적 오류"
        );
        job.claim(firstAttemptAt.plusMinutes(1), Duration.ofSeconds(1));
        job.retry(
                firstAttemptAt.plusMinutes(2),
                "TEMPORARY_FAILURE",
                "일시적 오류"
        );
        job.claim(firstAttemptAt.plusMinutes(2), Duration.ofSeconds(1));
        setId(job, id);
        return job;
    }

    private static void setId(ProcessingJob job, Long id) {
        ReflectionTestUtils.setField(job, "id", id);
    }
}
