package com.ward.techview.evaluation.service;

import com.ward.techview.evaluation.api.analysis.*;
import com.ward.techview.evaluation.api.feedback.*;
import com.ward.techview.interview.api.answer.AnswerAnalysisContext;
import com.ward.techview.interview.api.feedback.InterviewEvaluationContextApi;
import com.ward.techview.processing.api.analysis.AnswerAnalysisJobControl;
import com.ward.techview.processing.api.analysis.ClaimedAnswerAnalysisJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerAnalysisProcessor {
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(5);

    private final AnswerAnalysisJobControl jobControl;
    private final InterviewEvaluationContextApi interviewEvaluationContextApi;
    private final AnswerAnalysisProvider analysisProvider;
    private final AnswerAnalysisCompletionService completionService;

    public void process(Long processingJobId) {
        jobControl.claim(processingJobId)
                .ifPresent(this::processClaimedJob);
    }

    private void processClaimedJob(ClaimedAnswerAnalysisJob job) {
        try {
            AnswerAnalysisContext context = interviewEvaluationContextApi.getAnalysisContext(job.interviewSessionId(), job.answerId());

            AnswerAnalysisPrompt prompt = toPrompt(context);

            AnswerAnalysisResult result = analysisProvider.analyze(prompt);

            validateResult(prompt, result);

            completionService.complete(job, result);
        } catch (AnswerAnalysisException exception) {
            handleAnalysisFailure(job, exception);
        }
    }

    private AnswerAnalysisPrompt toPrompt(AnswerAnalysisContext context) {
        return new AnswerAnalysisPrompt(
                context.category(),
                context.coreQuestion(),
                context.followUpQuestion(),
                context.transcript(),
                context.followUpAllowed()
        );
    }

    private void handleAnalysisFailure(ClaimedAnswerAnalysisJob job, AnswerAnalysisException exception) {
        String errorCode = "ANSWER_ANALYSIS_" + exception.getFailure().name();

        switch (exception.getFailure()) {
            case RATE_LIMITED, TEMPORARY_FAILURE, INVALID_RESPONSE, UNKNOWN -> retry(job, errorCode, exception);
            case AUTHENTICATION_FAILED -> fail(job, errorCode, exception);
        }
    }

    private void validateResult(AnswerAnalysisPrompt prompt, AnswerAnalysisResult result) {
        if (!prompt.followUpAllowed() && result.followUpDecision() == FollowUpDecision.ASK) {
            throw new AnswerAnalysisException(
                    AnswerAnalysisFailure.INVALID_RESPONSE,
                    "꼬리 질문 답변에는 추가 꼬리 질문을 생성할 수 없습니다.",
                    null
            );
        }
    }

    private void retry(ClaimedAnswerAnalysisJob job, String errorCode, RuntimeException exception) {
        OffsetDateTime nextAttemptAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plus(calculateRetryDelay(job.attemptCount()));

        boolean updated = jobControl.retry(
                job.processingJobId(),
                job.attemptCount(),
                nextAttemptAt,
                errorCode,
                resolveErrorMessage(exception)
        );

        if (!updated) {
            log.info(
                    "처리 권한이 만료된 답변 분석 작업의 재시도 요청을 무시합니다. " + "processingJobId={}, attemptCount={}",
                    job.processingJobId(),
                    job.attemptCount()
            );
        }
    }

    private void fail(ClaimedAnswerAnalysisJob job, String errorCode, RuntimeException exception) {
        boolean updated = jobControl.fail(
                job.processingJobId(),
                job.attemptCount(),
                errorCode,
                resolveErrorMessage(exception)
        );

        if (!updated) {
            log.info(
                    "처리 권한이 만료된 답변 분석 작업의 실패 요청을 무시합니다. " + "processingJobId={}, attemptCount={}",
                    job.processingJobId(),
                    job.attemptCount()
            );
        }
    }

    private Duration calculateRetryDelay(int attemptCount) {
        long multiplier = 1L << Math.max(0, attemptCount - 1);

        Duration calculated = INITIAL_RETRY_DELAY.multipliedBy(multiplier);

        if (calculated.compareTo(MAX_RETRY_DELAY) > 0) {
            return MAX_RETRY_DELAY;
        }

        return calculated;
    }

    private String resolveErrorMessage(RuntimeException exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return message;
    }
}
