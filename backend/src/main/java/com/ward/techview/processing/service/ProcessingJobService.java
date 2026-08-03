package com.ward.techview.processing.service;

import com.ward.techview.processing.api.ProcessingJobQueue;
import com.ward.techview.processing.api.ProcessingJobReceipt;
import com.ward.techview.processing.api.SttTranscriptionJobQueuedEvent;
import com.ward.techview.processing.entity.SttProcessingJob;
import com.ward.techview.processing.repository.SttProcessingJobRepository;
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
    private final SttProcessingJobRepository sttProcessingJobRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessingJobReceipt> findSttJob(Long answerId) {
        return sttProcessingJobRepository.findByAnswerId(answerId)
                .map(this::toReceipt);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSttJob(Long answerId) {
        return sttProcessingJobRepository.existsByAnswerId(answerId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProcessingJobReceipt createOrGetSttJob(
            Long interviewSessionId,
            Long answerId,
            String audioObjectKey,
            String sourceObjectEtag
    ) {
        return sttProcessingJobRepository.findByAnswerId(answerId)
                .map(this::toReceipt)
                .orElseGet(() -> createSttJob(
                        interviewSessionId,
                        answerId,
                        audioObjectKey,
                        sourceObjectEtag
                ));
    }

    private ProcessingJobReceipt toReceipt(SttProcessingJob job) {
        return new ProcessingJobReceipt(
                job.getId(),
                job.getInterviewSessionId()
        );
    }

    private ProcessingJobReceipt createSttJob(
            Long interviewSessionId,
            Long answerId,
            String audioObjectKey,
            String sourceObjectEtag
    ) {
        SttProcessingJob job = SttProcessingJob.create(
                interviewSessionId,
                answerId,
                audioObjectKey,
                sourceObjectEtag,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        sttProcessingJobRepository.save(job);

        eventPublisher.publishEvent(new SttTranscriptionJobQueuedEvent(job.getId()));

        return toReceipt(job);
    }
}
