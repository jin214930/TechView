package com.ward.techview.evaluation.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.evaluation.dto.InterviewFeedbackResponse;
import com.ward.techview.evaluation.entity.InterviewFeedback;
import com.ward.techview.evaluation.repository.InterviewFeedbackRepository;
import com.ward.techview.evaluation.response.EvaluationResponseCode;
import com.ward.techview.interview.api.InterviewFeedbackAccessApi;
import com.ward.techview.processing.api.FinalFeedbackProcessingQuery;
import com.ward.techview.processing.api.FinalFeedbackProcessingSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InterviewFeedbackQueryService {
    private final InterviewFeedbackAccessApi interviewFeedbackAccessApi;
    private final InterviewFeedbackRepository interviewFeedbackRepository;
    private final FinalFeedbackProcessingQuery processingQuery;

    @Transactional(readOnly = true)
    public InterviewFeedbackResponse getFeedback(
            Long memberId,
            Long interviewSessionId
    ) {
        Objects.requireNonNull(memberId, "회원 ID는 필수입니다.");
        Objects.requireNonNull(interviewSessionId, "면접 세션 ID는 필수입니다.");

        interviewFeedbackAccessApi.validateCompletedInterview(memberId, interviewSessionId);

        Optional<InterviewFeedback> feedback = interviewFeedbackRepository.findByInterviewSessionId(interviewSessionId);

        if (feedback.isPresent()) {
            return InterviewFeedbackResponse.completed(feedback.get());
        }

        FinalFeedbackProcessingSnapshot snapshot = processingQuery.getStatus(interviewSessionId);

        return switch (snapshot.status()) {
            case PROCESSING -> InterviewFeedbackResponse.processing(interviewSessionId);
            case FAILED -> InterviewFeedbackResponse.failed(interviewSessionId);
            case NOT_STARTED, SUCCEEDED ->
                    throw new BusinessException(EvaluationResponseCode.INTERVIEW_FEEDBACK_STATE_INVALID);
        };
    }
}
