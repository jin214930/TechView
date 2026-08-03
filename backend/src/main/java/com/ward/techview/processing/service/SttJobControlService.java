package com.ward.techview.processing.service;

import com.ward.techview.processing.api.ClaimedSttJob;
import com.ward.techview.processing.api.SttJobControl;
import com.ward.techview.processing.config.ProcessingWorkerProperties;
import com.ward.techview.processing.entity.SttProcessingJob;
import com.ward.techview.processing.repository.SttProcessingJobRepository;
import lombok.RequiredArgsConstructor;
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
        return findCurrentAttempt(processingJobId, attemptCount)
                .map(job -> {
                    job.retry(nextAttemptAt, errorCode, errorMessage);
                    return true;
                })
                .orElse(false);
    }

    @Override
    @Transactional
    public boolean fail(Long processingJobId, int attemptCount, String errorCode, String errorMessage) {
        return findCurrentAttempt(processingJobId, attemptCount)
                .map(job -> {
                    job.fail(errorCode, errorMessage);
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
                job.getAttemptCount()
        );
    }

    private Optional<SttProcessingJob> findCurrentAttempt(Long processingJobId, int attemptCount) {
        return sttProcessingJobRepository
                .findByIdForUpdate(processingJobId)
                .filter(job -> job.isCurrentAttempt(attemptCount));
    }
}
