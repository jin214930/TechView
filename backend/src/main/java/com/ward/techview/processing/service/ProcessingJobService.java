package com.ward.techview.processing.service;

import com.ward.techview.processing.api.ProcessingJobQueue;
import com.ward.techview.processing.api.ProcessingJobReceipt;
import com.ward.techview.processing.api.SttTranscriptionJobQueuedEvent;
import com.ward.techview.processing.entity.ProcessingJob;
import com.ward.techview.processing.entity.ProcessingJobType;
import com.ward.techview.processing.repository.ProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProcessingJobService implements ProcessingJobQueue {
    private final ProcessingJobRepository processingJobRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessingJobReceipt> findSttJob(Long answerId) {
        return processingJobRepository.findByAnswerIdAndJobType(answerId, ProcessingJobType.STT_TRANSCRIPTION)
                .map(this::toReceipt);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSttJob(Long answerId) {
        return processingJobRepository.existsByAnswerIdAndJobType(answerId, ProcessingJobType.STT_TRANSCRIPTION);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProcessingJobReceipt createOrGetSttJob(Long interviewSessionId, Long answerId) {
        return processingJobRepository.findByAnswerIdAndJobType(answerId, ProcessingJobType.STT_TRANSCRIPTION)
                .map(this::toReceipt)
                .orElseGet(() -> createSttJob(interviewSessionId, answerId));
    }

    private ProcessingJobReceipt toReceipt(ProcessingJob job) {
        return new ProcessingJobReceipt(
                job.getId(),
                job.getInterviewSessionId(),
                job.getAnswerId()
        );
    }

    private ProcessingJobReceipt createSttJob(Long interviewSessionId, Long answerId) {
        ProcessingJob job = ProcessingJob.createSttTranscription(
                interviewSessionId,
                answerId,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        processingJobRepository.save(job);

        eventPublisher.publishEvent(new SttTranscriptionJobQueuedEvent(job.getId()));

        return toReceipt(job);
    }
}
