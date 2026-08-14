package com.ward.techview.evaluation.service;

import com.ward.techview.evaluation.api.feedback.InterviewFeedbackResult;
import com.ward.techview.evaluation.entity.InterviewFeedback;
import com.ward.techview.evaluation.repository.InterviewFeedbackRepository;
import com.ward.techview.processing.api.feedback.ClaimedFinalFeedbackJob;
import com.ward.techview.processing.api.feedback.FinalFeedbackJobControl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InterviewFeedbackCompletionService {
    private final InterviewFeedbackRepository interviewFeedbackRepository;
    private final FinalFeedbackJobControl jobControl;

    @Transactional
    public void complete(ClaimedFinalFeedbackJob job, InterviewFeedbackResult result) {
        Objects.requireNonNull(job);
        Objects.requireNonNull(result);

        succeedJob(job);

        InterviewFeedback feedback = InterviewFeedback.create(
                job.interviewSessionId(),
                result.overallSummary(),
                result.strengths(),
                result.improvements(),
                result.categoryFeedbacks(),
                result.recommendedTopics(),
                result.model(),
                result.promptVersion()
        );

        interviewFeedbackRepository.save(feedback);
    }

    private void succeedJob(ClaimedFinalFeedbackJob job) {
        boolean succeeded = jobControl.succeed(job.processingJobId(), job.attemptCount());

        if (!succeeded) {
            throw new IllegalStateException("종합 피드백 작업 처리 권한이 만료되었습니다.");
        }
    }
}
