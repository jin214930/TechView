package com.ward.techview.processing.service;

import com.ward.techview.processing.api.ProcessingJobReceipt;
import com.ward.techview.processing.api.SttTranscriptionJobQueuedEvent;
import com.ward.techview.processing.entity.ProcessingJob;
import com.ward.techview.processing.entity.ProcessingJobType;
import com.ward.techview.processing.repository.ProcessingJobRepository;
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

    @Mock
    private ProcessingJobRepository processingJobRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProcessingJobService processingJobService;

    @Test
    @DisplayName("기존 STT 작업이 있으면 해당 작업을 반환하고 이벤트를 발행하지 않는다")
    void shouldReturnExistingSttJobWithoutPublishingEvent() {
        ProcessingJob existingJob = processingJob();
        when(processingJobRepository.findByAnswerIdAndJobType(
                ANSWER_ID,
                ProcessingJobType.STT_TRANSCRIPTION
        )).thenReturn(Optional.of(existingJob));

        ProcessingJobReceipt result = processingJobService.createOrGetSttJob(
                SESSION_ID,
                ANSWER_ID
        );

        assertReceipt(result);
        verify(processingJobRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("신규 STT 작업을 저장하고 작업 대기 이벤트를 발행한다")
    void shouldCreateSttJobAndPublishQueuedEvent() {
        when(processingJobRepository.findByAnswerIdAndJobType(
                ANSWER_ID,
                ProcessingJobType.STT_TRANSCRIPTION
        )).thenReturn(Optional.empty());
        when(processingJobRepository.save(any(ProcessingJob.class)))
                .thenAnswer(invocation -> {
                    ProcessingJob job = invocation.getArgument(0);
                    ReflectionTestUtils.setField(job, "id", PROCESSING_JOB_ID);
                    return job;
                });

        ProcessingJobReceipt result = processingJobService.createOrGetSttJob(
                SESSION_ID,
                ANSWER_ID
        );

        assertReceipt(result);
        ArgumentCaptor<SttTranscriptionJobQueuedEvent> eventCaptor =
                ArgumentCaptor.forClass(SttTranscriptionJobQueuedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(
                PROCESSING_JOB_ID,
                eventCaptor.getValue().processingJobId()
        );
    }

    private static ProcessingJob processingJob() {
        ProcessingJob job = ProcessingJob.createSttTranscription(
                SESSION_ID,
                ANSWER_ID,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(job, "id", PROCESSING_JOB_ID);
        return job;
    }

    private static void assertReceipt(ProcessingJobReceipt result) {
        assertAll(
                () -> assertEquals(PROCESSING_JOB_ID, result.processingJobId()),
                () -> assertEquals(SESSION_ID, result.interviewSessionId()),
                () -> assertEquals(ANSWER_ID, result.answerId())
        );
    }
}
