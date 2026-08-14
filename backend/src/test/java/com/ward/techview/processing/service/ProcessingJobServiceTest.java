package com.ward.techview.processing.service;

import com.ward.techview.processing.api.analysis.AnswerAnalysisJobQueuedEvent;
import com.ward.techview.processing.api.feedback.FinalFeedbackJobQueuedEvent;
import com.ward.techview.processing.api.job.ProcessingJobReceipt;
import com.ward.techview.processing.api.stt.SttTranscriptionJobQueuedEvent;
import com.ward.techview.processing.entity.AnswerAnalysisProcessingJob;
import com.ward.techview.processing.entity.FinalFeedbackProcessingJob;
import com.ward.techview.processing.entity.SttProcessingJob;
import com.ward.techview.processing.repository.AnswerAnalysisProcessingJobRepository;
import com.ward.techview.processing.repository.FinalFeedbackProcessingJobRepository;
import com.ward.techview.processing.repository.SttProcessingJobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessingJobServiceTest {

    private static final Long SESSION_ID = 10L;
    private static final Long ANSWER_ID = 1000L;
    private static final Long PROCESSING_JOB_ID = 2000L;
    private static final String AUDIO_OBJECT_KEY = "interviews/10/turns/100/audio-key";
    private static final String SOURCE_OBJECT_ETAG = "verified-audio-etag";

    @Mock
    private SttProcessingJobRepository sttProcessingJobRepository;

    @Mock
    private AnswerAnalysisProcessingJobRepository answerAnalysisProcessingJobRepository;

    @Mock
    private FinalFeedbackProcessingJobRepository finalFeedbackProcessingJobRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProcessingJobService processingJobService;

    @Test
    @DisplayName("기존 STT 작업이 있으면 해당 작업을 반환하고 이벤트를 발행하지 않는다")
    void shouldReturnExistingSttJobWithoutPublishingEvent() {
        SttProcessingJob existingJob = processingJob();
        when(sttProcessingJobRepository.findByAnswerId(ANSWER_ID))
                .thenReturn(Optional.of(existingJob));

        ProcessingJobReceipt result = processingJobService.createOrGetSttJob(
                SESSION_ID,
                ANSWER_ID,
                AUDIO_OBJECT_KEY,
                SOURCE_OBJECT_ETAG
        );

        assertReceipt(result);
        verify(sttProcessingJobRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("신규 STT 작업과 검증된 객체 정보를 저장하고 작업 대기 이벤트를 발행한다")
    void shouldCreateSttJobAndPublishQueuedEvent() {
        when(sttProcessingJobRepository.findByAnswerId(ANSWER_ID))
                .thenReturn(Optional.empty());
        when(sttProcessingJobRepository.save(any(SttProcessingJob.class)))
                .thenAnswer(invocation -> {
                    SttProcessingJob job = invocation.getArgument(0);
                    ReflectionTestUtils.setField(job, "id", PROCESSING_JOB_ID);
                    return job;
                });

        ProcessingJobReceipt result = processingJobService.createOrGetSttJob(
                SESSION_ID,
                ANSWER_ID,
                AUDIO_OBJECT_KEY,
                SOURCE_OBJECT_ETAG
        );

        assertReceipt(result);
        ArgumentCaptor<SttProcessingJob> jobCaptor =
                ArgumentCaptor.forClass(SttProcessingJob.class);
        verify(sttProcessingJobRepository).save(jobCaptor.capture());
        SttProcessingJob savedJob = jobCaptor.getValue();
        assertAll(
                () -> assertEquals(ANSWER_ID, savedJob.getAnswerId()),
                () -> assertEquals(AUDIO_OBJECT_KEY, savedJob.getAudioObjectKey()),
                () -> assertEquals(SOURCE_OBJECT_ETAG, savedJob.getSourceObjectEtag())
        );

        ArgumentCaptor<SttTranscriptionJobQueuedEvent> eventCaptor =
                ArgumentCaptor.forClass(SttTranscriptionJobQueuedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(PROCESSING_JOB_ID, eventCaptor.getValue().processingJobId());
    }

    @Test
    @DisplayName("기존 답변 분석 작업이 있으면 해당 작업을 반환하고 이벤트를 발행하지 않는다")
    void shouldReturnExistingAnswerAnalysisJobWithoutPublishingEvent() {
        AnswerAnalysisProcessingJob existingJob = answerAnalysisProcessingJob();
        when(answerAnalysisProcessingJobRepository.findByAnswerId(ANSWER_ID))
                .thenReturn(Optional.of(existingJob));

        ProcessingJobReceipt result = processingJobService.createOrGetAnswerAnalysisJob(
                SESSION_ID,
                ANSWER_ID
        );

        assertReceipt(result);
        verify(answerAnalysisProcessingJobRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("신규 답변 분석 작업을 저장하고 작업 대기 이벤트를 발행한다")
    void shouldCreateAnswerAnalysisJobAndPublishQueuedEvent() {
        when(answerAnalysisProcessingJobRepository.findByAnswerId(ANSWER_ID))
                .thenReturn(Optional.empty());
        when(answerAnalysisProcessingJobRepository.save(any(AnswerAnalysisProcessingJob.class)))
                .thenAnswer(invocation -> {
                    AnswerAnalysisProcessingJob job = invocation.getArgument(0);
                    ReflectionTestUtils.setField(job, "id", PROCESSING_JOB_ID);
                    return job;
                });

        ProcessingJobReceipt result = processingJobService.createOrGetAnswerAnalysisJob(
                SESSION_ID,
                ANSWER_ID
        );

        assertReceipt(result);
        ArgumentCaptor<AnswerAnalysisProcessingJob> jobCaptor =
                ArgumentCaptor.forClass(AnswerAnalysisProcessingJob.class);
        verify(answerAnalysisProcessingJobRepository).save(jobCaptor.capture());
        assertEquals(ANSWER_ID, jobCaptor.getValue().getAnswerId());

        ArgumentCaptor<AnswerAnalysisJobQueuedEvent> eventCaptor =
                ArgumentCaptor.forClass(AnswerAnalysisJobQueuedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(PROCESSING_JOB_ID, eventCaptor.getValue().processingJobId());
    }

    @Test
    @DisplayName("기존 종합 피드백 작업이 있으면 해당 작업을 반환하고 이벤트를 발행하지 않는다")
    void shouldReturnExistingFinalFeedbackJobWithoutPublishingEvent() {
        FinalFeedbackProcessingJob existingJob = finalFeedbackProcessingJob();
        when(finalFeedbackProcessingJobRepository.findByInterviewSessionId(SESSION_ID))
                .thenReturn(Optional.of(existingJob));

        ProcessingJobReceipt result = processingJobService.createOrGetFinalFeedbackJob(SESSION_ID);

        assertReceipt(result);
        verify(finalFeedbackProcessingJobRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("신규 종합 피드백 작업을 저장하고 작업 대기 이벤트를 발행한다")
    void shouldCreateFinalFeedbackJobAndPublishQueuedEvent() {
        when(finalFeedbackProcessingJobRepository.findByInterviewSessionId(SESSION_ID))
                .thenReturn(Optional.empty());
        when(finalFeedbackProcessingJobRepository.save(any(FinalFeedbackProcessingJob.class)))
                .thenAnswer(invocation -> {
                    FinalFeedbackProcessingJob job = invocation.getArgument(0);
                    ReflectionTestUtils.setField(job, "id", PROCESSING_JOB_ID);
                    return job;
                });

        ProcessingJobReceipt result = processingJobService.createOrGetFinalFeedbackJob(SESSION_ID);

        assertReceipt(result);
        verify(finalFeedbackProcessingJobRepository).save(any(FinalFeedbackProcessingJob.class));

        ArgumentCaptor<FinalFeedbackJobQueuedEvent> eventCaptor =
                ArgumentCaptor.forClass(FinalFeedbackJobQueuedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(PROCESSING_JOB_ID, eventCaptor.getValue().processingJobId());
    }

    private static SttProcessingJob processingJob() {
        SttProcessingJob job = SttProcessingJob.create(
                SESSION_ID,
                ANSWER_ID,
                AUDIO_OBJECT_KEY,
                SOURCE_OBJECT_ETAG,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(job, "id", PROCESSING_JOB_ID);
        return job;
    }

    private static AnswerAnalysisProcessingJob answerAnalysisProcessingJob() {
        AnswerAnalysisProcessingJob job = AnswerAnalysisProcessingJob.create(
                SESSION_ID,
                ANSWER_ID,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(job, "id", PROCESSING_JOB_ID);
        return job;
    }

    private static FinalFeedbackProcessingJob finalFeedbackProcessingJob() {
        FinalFeedbackProcessingJob job = FinalFeedbackProcessingJob.create(
                SESSION_ID,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(job, "id", PROCESSING_JOB_ID);
        return job;
    }

    private static void assertReceipt(ProcessingJobReceipt result) {
        assertAll(
                () -> assertEquals(PROCESSING_JOB_ID, result.processingJobId()),
                () -> assertEquals(SESSION_ID, result.interviewSessionId())
        );
    }
}
