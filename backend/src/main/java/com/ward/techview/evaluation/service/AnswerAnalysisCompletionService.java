package com.ward.techview.evaluation.service;

import com.ward.techview.evaluation.api.AnswerAnalysisResult;
import com.ward.techview.evaluation.entity.AnswerAnalysis;
import com.ward.techview.evaluation.repository.AnswerAnalysisRepository;
import com.ward.techview.interview.api.InterviewAdvanceCommand;
import com.ward.techview.interview.api.InterviewProgressApi;
import com.ward.techview.processing.api.AnswerAnalysisJobControl;
import com.ward.techview.processing.api.ClaimedAnswerAnalysisJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AnswerAnalysisCompletionService {
    private final AnswerAnalysisRepository answerAnalysisRepository;
    private final InterviewProgressApi interviewProgressApi;
    private final AnswerAnalysisJobControl jobControl;

    @Transactional
    public void complete(ClaimedAnswerAnalysisJob job, AnswerAnalysisResult result) {
        Objects.requireNonNull(job);
        Objects.requireNonNull(result);

        saveAnalysis(job.answerId(), result);

        interviewProgressApi.advanceAfterAnalysis(
                new InterviewAdvanceCommand(
                        job.interviewSessionId(),
                        job.answerId(),
                        result.followUpQuestion(),
                        OffsetDateTime.now(ZoneOffset.UTC)
                )
        );

        succeedJob(job);
    }

    private void saveAnalysis(Long answerId, AnswerAnalysisResult result) {
        AnswerAnalysis analysis = AnswerAnalysis.create(
                answerId,
                result.summary(),
                result.strengths(),
                result.improvements(),
                result.followUpDecision(),
                result.followUpReason(),
                result.model(),
                result.promptVersion()
        );

        answerAnalysisRepository.save(analysis);
    }

    private void succeedJob(ClaimedAnswerAnalysisJob job) {
        boolean succeeded = jobControl.succeed(job.processingJobId(), job.attemptCount());

        if (!succeeded) {
            throw new IllegalStateException("답변 분석 작업 처리 권한이 만료되었습니다.");
        }
    }
}
