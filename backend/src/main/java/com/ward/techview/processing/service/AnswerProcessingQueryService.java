package com.ward.techview.processing.service;

import com.ward.techview.processing.api.analysis.AnswerProcessingQuery;
import com.ward.techview.processing.api.analysis.AnswerProcessingSnapshot;
import com.ward.techview.processing.entity.AnswerAnalysisProcessingJob;
import com.ward.techview.processing.entity.SttProcessingJob;
import com.ward.techview.processing.repository.AnswerAnalysisProcessingJobRepository;
import com.ward.techview.processing.repository.SttProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AnswerProcessingQueryService implements AnswerProcessingQuery {
    private final SttProcessingJobRepository sttJobRepository;
    private final AnswerAnalysisProcessingJobRepository analysisJobRepository;

    @Override
    @Transactional(readOnly = true)
    public AnswerProcessingSnapshot getStatus(Long answerId) {
        Objects.requireNonNull(answerId, "답변 ID는 필수입니다.");

        return analysisJobRepository.findByAnswerId(answerId)
                .map(this::fromAnalysisJob)
                .orElseGet(() -> getStatusFromSttJob(answerId));
    }

    private AnswerProcessingSnapshot getStatusFromSttJob(Long answerId) {
        return sttJobRepository.findByAnswerId(answerId)
                .map(this::fromSttJob)
                .orElseGet(AnswerProcessingSnapshot::notStarted);
    }

    private AnswerProcessingSnapshot fromAnalysisJob(AnswerAnalysisProcessingJob job) {
        return switch (job.getStatus()) {
            case QUEUED, PROCESSING, RETRY_WAIT -> AnswerProcessingSnapshot.processing();
            case SUCCEEDED -> AnswerProcessingSnapshot.completed();
            case FAILED -> AnswerProcessingSnapshot.failed(job.getErrorCode());
        };
    }

    private AnswerProcessingSnapshot fromSttJob(SttProcessingJob job) {
        return switch (job.getStatus()) {
            case QUEUED, PROCESSING, RETRY_WAIT, SUCCEEDED -> AnswerProcessingSnapshot.processing();
            case FAILED -> AnswerProcessingSnapshot.failed(job.getErrorCode());
        };
    }
}
