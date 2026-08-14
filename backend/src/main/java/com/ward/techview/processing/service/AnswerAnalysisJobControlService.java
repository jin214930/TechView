package com.ward.techview.processing.service;

import com.ward.techview.processing.api.analysis.AnswerAnalysisJobControl;
import com.ward.techview.processing.api.analysis.AnswerProcessingStatusChangedEvent;
import com.ward.techview.processing.api.analysis.ClaimedAnswerAnalysisJob;
import com.ward.techview.processing.config.ProcessingWorkerProperties;
import com.ward.techview.processing.entity.AnswerAnalysisProcessingJob;
import com.ward.techview.processing.entity.ProcessingJob;
import com.ward.techview.processing.entity.ProcessingJobStatus;
import com.ward.techview.processing.repository.AnswerAnalysisProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AnswerAnalysisJobControlService implements AnswerAnalysisJobControl {
    private final AnswerAnalysisProcessingJobRepository repository;
    private final ProcessingWorkerProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Optional<ClaimedAnswerAnalysisJob> claim(Long processingJobId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return repository.findByIdForUpdate(processingJobId)
                .filter(job -> job.isClaimable(now))
                .map(job -> claim(job, now));
    }

    @Override
    @Transactional
    public boolean succeed(Long processingJobId, int attemptCount) {
        return findCurrentAttempt(processingJobId, attemptCount)
                .map(job -> {
                    job.succeed();
                    publishStatusChanged(job);
                    return true;
                })
                .orElse(false);
    }

    @Override
    @Transactional
    public boolean retry(
            Long processingJobId,
            int attemptCount,
            OffsetDateTime nextAttemptAt,
            String errorCode,
            String errorMessage
    ) {
        return findCurrentAttempt(processingJobId, attemptCount)
                .map(job -> {
                    job.retry(nextAttemptAt, errorCode, errorMessage);
                    if (job.getStatus() == ProcessingJobStatus.FAILED) {
                        publishStatusChanged(job);
                    }
                    return true;
                })
                .orElse(false);
    }

    @Override
    @Transactional
    public boolean fail(
            Long processingJobId,
            int attemptCount,
            String errorCode,
            String errorMessage
    ) {
        return findCurrentAttempt(processingJobId, attemptCount)
                .map(job -> {
                    job.fail(errorCode, errorMessage);
                    publishStatusChanged(job);
                    return true;
                })
                .orElse(false);
    }

    private ClaimedAnswerAnalysisJob claim(AnswerAnalysisProcessingJob job, OffsetDateTime now) {
        job.claim(now, properties.leaseDuration());

        return new ClaimedAnswerAnalysisJob(
                job.getId(),
                job.getInterviewSessionId(),
                job.getAnswerId(),
                job.getAttemptCount()
        );
    }

    private Optional<AnswerAnalysisProcessingJob> findCurrentAttempt(Long processingJobId, int attemptCount) {
        return repository.findByIdForUpdate(processingJobId)
                .filter(job -> job.isCurrentAttempt(attemptCount));
    }

    private void publishStatusChanged(ProcessingJob job) {
        eventPublisher.publishEvent(
                new AnswerProcessingStatusChangedEvent(job.getInterviewSessionId())
        );
    }
}
