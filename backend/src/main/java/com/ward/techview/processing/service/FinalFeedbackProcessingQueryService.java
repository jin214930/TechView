package com.ward.techview.processing.service;

import com.ward.techview.processing.api.FinalFeedbackProcessingQuery;
import com.ward.techview.processing.api.FinalFeedbackProcessingSnapshot;
import com.ward.techview.processing.api.FinalFeedbackProcessingStatus;
import com.ward.techview.processing.entity.FinalFeedbackProcessingJob;
import com.ward.techview.processing.repository.FinalFeedbackProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Override
    @Transactional(readOnly = true)
    public Map<Long, FinalFeedbackProcessingSnapshot> getStatuses(Set<Long> interviewSessionIds) {
        Objects.requireNonNull(interviewSessionIds, "면접 세션 ID 목록은 필수입니다.");

        if (interviewSessionIds.isEmpty()) {
            return Map.of();
        }

        if (interviewSessionIds.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("면접 세션 ID는 null일 수 없습니다.");
        }

        Map<Long, FinalFeedbackProcessingSnapshot> snapshots = createNotStartedSnapshots(interviewSessionIds);

        jobRepository.findAllByInterviewSessionIdIn(interviewSessionIds)
                .forEach(job -> snapshots.put(job.getInterviewSessionId(), toSnapshot(job)));

        return Map.copyOf(snapshots);
    }

    private FinalFeedbackProcessingSnapshot toSnapshot(FinalFeedbackProcessingJob job) {
        FinalFeedbackProcessingStatus status = switch (job.getStatus()) {
            case QUEUED, PROCESSING, RETRY_WAIT -> FinalFeedbackProcessingStatus.PROCESSING;
            case SUCCEEDED -> FinalFeedbackProcessingStatus.SUCCEEDED;
            case FAILED -> FinalFeedbackProcessingStatus.FAILED;
        };

        return new FinalFeedbackProcessingSnapshot(status);
    }

    private Map<Long, FinalFeedbackProcessingSnapshot> createNotStartedSnapshots(Set<Long> interviewSessionIds) {
        FinalFeedbackProcessingSnapshot notStarted = new FinalFeedbackProcessingSnapshot(FinalFeedbackProcessingStatus.NOT_STARTED);

        return interviewSessionIds.stream()
                .collect(Collectors.toMap(Function.identity(), ignored -> notStarted));
    }
}
