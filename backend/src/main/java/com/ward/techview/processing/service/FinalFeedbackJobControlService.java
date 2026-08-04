package com.ward.techview.processing.service;

import com.ward.techview.processing.api.ClaimedFinalFeedbackJob;
import com.ward.techview.processing.api.FinalFeedbackJobControl;
import com.ward.techview.processing.config.ProcessingWorkerProperties;
import com.ward.techview.processing.entity.FinalFeedbackProcessingJob;
import com.ward.techview.processing.repository.FinalFeedbackProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FinalFeedbackJobControlService implements FinalFeedbackJobControl {
    private final FinalFeedbackProcessingJobRepository repository;
    private final ProcessingWorkerProperties properties;

    @Override
    @Transactional
    public Optional<ClaimedFinalFeedbackJob> claim(Long processingJobId) {
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
                    return true;
                })
                .orElse(false);
    }

    private ClaimedFinalFeedbackJob claim(FinalFeedbackProcessingJob job, OffsetDateTime now) {
        job.claim(now, properties.leaseDuration());

        return new ClaimedFinalFeedbackJob(
                job.getId(),
                job.getInterviewSessionId(),
                job.getAttemptCount()
        );
    }

    private Optional<FinalFeedbackProcessingJob> findCurrentAttempt(Long processingJobId, int attemptCount) {
        return repository.findByIdForUpdate(processingJobId)
                .filter(job -> job.isCurrentAttempt(attemptCount));
    }
}
