package com.ward.techview.processing.service;

import com.ward.techview.processing.api.*;
import com.ward.techview.processing.config.ProcessingWorkerProperties;
import com.ward.techview.processing.entity.ProcessingJob;
import com.ward.techview.processing.entity.ProcessingJobStatus;
import com.ward.techview.processing.entity.SttProcessingJob;
import com.ward.techview.processing.repository.ProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcessingJobPollingService {
    private final ProcessingJobRepository processingJobRepository;
    private final ProcessingWorkerProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public int pollAndPublish() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Pageable limit = PageRequest.of(0, properties.pollingBatchSize());

        failExhaustedJobs(now, limit);

        List<ProcessingJob> jobs = processingJobRepository.findDispatchableJobs(
                List.of(ProcessingJobStatus.QUEUED, ProcessingJobStatus.RETRY_WAIT),
                ProcessingJobStatus.PROCESSING,
                now,
                limit
        );

        jobs.forEach(this::publishDispatchEvent);

        return jobs.size();
    }

    private void failExhaustedJobs(OffsetDateTime now, Pageable limit) {
        List<ProcessingJob> exhaustedJobs = processingJobRepository.findExhaustedExpiredJobs(
                ProcessingJobStatus.PROCESSING,
                now,
                limit
        );

        exhaustedJobs.forEach(job -> {
            job.fail(
                    "WORKER_LEASE_EXPIRED",
                    "최대 시도 횟수에서 작업 처리 lease가 만료되었습니다."
            );

            if (job instanceof SttProcessingJob sttJob) {
                sttJob.scheduleAudioCleanup(now);
            }

            publishTerminalStatusChangedEvent(job);
        });
    }

    private void publishDispatchEvent(ProcessingJob job) {
        Object event = switch (job.getJobType()) {
            case STT_TRANSCRIPTION -> new SttTranscriptionJobQueuedEvent(job.getId());
            case ANSWER_ANALYSIS -> new AnswerAnalysisJobQueuedEvent(job.getId());
            case FINAL_FEEDBACK_GENERATION -> new FinalFeedbackJobQueuedEvent(job.getId());
        };

        eventPublisher.publishEvent(event);
    }

    private void publishTerminalStatusChangedEvent(ProcessingJob job) {
        Object event = switch (job.getJobType()) {
            case STT_TRANSCRIPTION, ANSWER_ANALYSIS ->
                    new AnswerProcessingStatusChangedEvent(job.getInterviewSessionId());
            case FINAL_FEEDBACK_GENERATION ->
                    new FinalFeedbackProcessingStatusChangedEvent(job.getInterviewSessionId());
        };

        eventPublisher.publishEvent(event);
    }
}
