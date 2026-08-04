package com.ward.techview.processing.service;

import com.ward.techview.processing.api.FinalFeedbackProcessingQuery;
import com.ward.techview.processing.api.FinalFeedbackProcessingSnapshot;
import com.ward.techview.processing.api.FinalFeedbackProcessingStatus;
import com.ward.techview.processing.entity.FinalFeedbackProcessingJob;
import com.ward.techview.processing.repository.FinalFeedbackProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FinalFeedbackProcessingQueryService implements FinalFeedbackProcessingQuery {
    private final FinalFeedbackProcessingJobRepository jobRepository;

    @Override
    @Transactional(readOnly = true)
    public FinalFeedbackProcessingSnapshot getStatus(Long interviewSessionId) {
        Objects.requireNonNull(interviewSessionId, "면접 세션 ID는 필수입니다.");

        return jobRepository.findByInterviewSessionId(interviewSessionId)
                .map(this::toSnapshot)
                .orElseGet(() -> new FinalFeedbackProcessingSnapshot(FinalFeedbackProcessingStatus.NOT_STARTED));
    }

    private FinalFeedbackProcessingSnapshot toSnapshot(FinalFeedbackProcessingJob job) {
        FinalFeedbackProcessingStatus status = switch (job.getStatus()) {
            case QUEUED, PROCESSING, RETRY_WAIT -> FinalFeedbackProcessingStatus.PROCESSING;
            case SUCCEEDED -> FinalFeedbackProcessingStatus.SUCCEEDED;
            case FAILED -> FinalFeedbackProcessingStatus.FAILED;
        };

        return new FinalFeedbackProcessingSnapshot(status);
    }
}
