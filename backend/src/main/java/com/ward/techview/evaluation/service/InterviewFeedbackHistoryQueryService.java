package com.ward.techview.evaluation.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.common.response.PageResponse;
import com.ward.techview.evaluation.dto.InterviewFeedbackStatus;
import com.ward.techview.evaluation.dto.InterviewHistoryItemResponse;
import com.ward.techview.evaluation.repository.InterviewFeedbackRepository;
import com.ward.techview.evaluation.response.EvaluationResponseCode;
import com.ward.techview.interview.api.CompletedInterviewSummary;
import com.ward.techview.interview.api.InterviewHistoryQueryApi;
import com.ward.techview.processing.api.FinalFeedbackProcessingQuery;
import com.ward.techview.processing.api.FinalFeedbackProcessingSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterviewFeedbackHistoryQueryService {
    private final InterviewHistoryQueryApi interviewHistoryQueryApi;
    private final InterviewFeedbackRepository interviewFeedbackRepository;
    private final FinalFeedbackProcessingQuery processingQuery;

    @Transactional(readOnly = true)
    public PageResponse<InterviewHistoryItemResponse> getCompletedInterviews(Long memberId, int page, int size) {
        Objects.requireNonNull(memberId, "회원 ID는 필수입니다.");

        Page<CompletedInterviewSummary> interviews = interviewHistoryQueryApi.getCompletedInterviews(memberId, page, size);

        if (interviews.isEmpty()) {
            return PageResponse.from(
                    interviews,
                    interview -> {
                        throw new IllegalStateException("빈 페이지에서는 호출되지 않습니다.");
                    }
            );
        }

        Set<Long> sessionIds = interviews.getContent().stream()
                .map(CompletedInterviewSummary::sessionId)
                .collect(Collectors.toSet());

        Set<Long> completedFeedbackSessionIds = interviewFeedbackRepository.findExistingInterviewSessionIds(sessionIds);

        Set<Long> sessionsWithoutFeedback = new HashSet<>(sessionIds);
        sessionsWithoutFeedback.removeAll(completedFeedbackSessionIds);

        Map<Long, FinalFeedbackProcessingSnapshot> processingStatuses = processingQuery.getStatuses(sessionsWithoutFeedback);

        return PageResponse.from(
                interviews,
                interview -> InterviewHistoryItemResponse.of(
                        interview,
                        resolveStatus(
                                interview.sessionId(),
                                completedFeedbackSessionIds,
                                processingStatuses
                        )
                )
        );
    }

    private InterviewFeedbackStatus resolveStatus(
            Long sessionId,
            Set<Long> completedFeedbackSessionIds,
            Map<Long, FinalFeedbackProcessingSnapshot> processingStatuses
    ) {
        if (completedFeedbackSessionIds.contains(sessionId)) {
            return InterviewFeedbackStatus.COMPLETED;
        }

        FinalFeedbackProcessingSnapshot snapshot = processingStatuses.get(sessionId);

        if (snapshot == null) {
            throw new BusinessException(EvaluationResponseCode.INTERVIEW_FEEDBACK_STATE_INVALID);
        }

        return switch (snapshot.status()) {
            case PROCESSING -> InterviewFeedbackStatus.PROCESSING;
            case FAILED -> InterviewFeedbackStatus.FAILED;
            case NOT_STARTED, SUCCEEDED ->
                    throw new BusinessException(EvaluationResponseCode.INTERVIEW_FEEDBACK_STATE_INVALID);
        };
    }
}
