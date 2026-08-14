package com.ward.techview.processing.service;

import com.ward.techview.processing.api.analysis.AnswerProcessingStatusChangedEvent;
import com.ward.techview.processing.api.stt.ClaimedSttJob;
import com.ward.techview.processing.api.stt.SttJobControl;
import com.ward.techview.processing.config.ProcessingWorkerProperties;
import com.ward.techview.processing.entity.ProcessingJob;
import com.ward.techview.processing.entity.ProcessingJobStatus;
import com.ward.techview.processing.entity.SttProcessingJob;
import com.ward.techview.processing.entity.SttProcessingStage;
import com.ward.techview.processing.repository.SttProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SttJobControlService implements SttJobControl {
    private final SttProcessingJobRepository sttProcessingJobRepository;
    private final ProcessingWorkerProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Optional<ClaimedSttJob> claim(Long processingJobId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return sttProcessingJobRepository
                .findByIdForUpdate(processingJobId)
                .filter(job -> job.isClaimable(now))
                .map(job -> claim(job, now));
    }

    @Override
    @Transactional
    public boolean advanceToCleanup(Long processingJobId, int attemptCount) {
        return findCurrentAttempt(processingJobId, attemptCount)
                .map(job -> {
                    job.advanceToCleanup();
                    return true;
                })
                .orElse(false);
    }

    @Override
    @Transactional
    public boolean succeed(Long processingJobId, int attemptCount) {
        return findCurrentAttempt(processingJobId, attemptCount)
                .map(job -> {
                    job.succeed();
                    return true;
                })
                .orElse(false);
    }

    @Override
    @Transactional
    public boolean retry(Long processingJobId, int attemptCount, OffsetDateTime nextAttemptAt, String errorCode, String errorMessage) {
        OffsetDateTime failedAt = OffsetDateTime.now(ZoneOffset.UTC);

        return findCurrentAttempt(processingJobId, attemptCount)
                .map(job -> {
                    job.retry(nextAttemptAt, errorCode, errorMessage);
                    if (job.getStatus() == ProcessingJobStatus.FAILED) {
                        job.scheduleAudioCleanup(failedAt);
                        publishStatusChanged(job);
                    }
                    return true;
                })
                .orElse(false);
    }

    @Override
    @Transactional
    public boolean fail(Long processingJobId, int attemptCount, String errorCode, String errorMessage) {
        OffsetDateTime failedAt = OffsetDateTime.now(ZoneOffset.UTC);

        return findCurrentAttempt(processingJobId, attemptCount)
                .map(job -> {
                    job.fail(errorCode, errorMessage);
                    job.scheduleAudioCleanup(failedAt);
                    publishStatusChanged(job);
                    return true;
                })
                .orElse(false);
    }

    private ClaimedSttJob claim(SttProcessingJob job, OffsetDateTime now) {
        job.claim(now, properties.leaseDuration());

        return new ClaimedSttJob(
                job.getId(),
                job.getInterviewSessionId(),
                job.getAnswerId(),
                job.getAudioObjectKey(),
                job.getSourceObjectEtag(),
                job.getProcessingStage() == SttProcessingStage.CLEANUP,
                job.getAttemptCount()
        );
    }

    private Optional<SttProcessingJob> findCurrentAttempt(Long processingJobId, int attemptCount) {
        return sttProcessingJobRepository
                .findByIdForUpdate(processingJobId)
                .filter(job -> job.isCurrentAttempt(attemptCount));
    }

    private void publishStatusChanged(ProcessingJob job) {
        eventPublisher.publishEvent(
                new AnswerProcessingStatusChangedEvent(job.getInterviewSessionId())
        );
    }
}
