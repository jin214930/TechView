package com.ward.techview.processing.service;

import com.ward.techview.processing.api.cleanup.SessionProcessingCleanupApi;
import com.ward.techview.processing.repository.ProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SessionProcessingCleanupService implements SessionProcessingCleanupApi {
    private final ProcessingJobRepository processingJobRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean canDeleteSession(Long interviewSessionId, OffsetDateTime now) {
        Objects.requireNonNull(interviewSessionId, "면접 세션 ID는 필수입니다.");
        Objects.requireNonNull(now, "현재 시각은 필수입니다.");

        return processingJobRepository
                .findAllByInterviewSessionIdForUpdate(interviewSessionId)
                .stream()
                .noneMatch(job -> job.isActivelyProcessing(now));
    }
}
