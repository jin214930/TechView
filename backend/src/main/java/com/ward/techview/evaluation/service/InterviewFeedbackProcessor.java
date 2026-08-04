package com.ward.techview.evaluation.service;

import com.ward.techview.evaluation.api.InterviewFeedbackException;
import com.ward.techview.evaluation.api.InterviewFeedbackPrompt;
import com.ward.techview.evaluation.api.InterviewFeedbackProvider;
import com.ward.techview.evaluation.api.InterviewFeedbackResult;
import com.ward.techview.processing.api.ClaimedFinalFeedbackJob;
import com.ward.techview.processing.api.FinalFeedbackJobControl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewFeedbackProcessor {
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(5);

    private final FinalFeedbackJobControl jobControl;
    private final InterviewFeedbackPromptService promptService;
    private final InterviewFeedbackProvider feedbackProvider;
    private final InterviewFeedbackCompletionService completionService;

    public void process(Long processingJobId) {
        jobControl.claim(processingJobId)
                .ifPresent(this::processClaimedJob);
    }

    private void processClaimedJob(ClaimedFinalFeedbackJob job) {
        try {
            InterviewFeedbackPrompt prompt = promptService.create(job.interviewSessionId());

            InterviewFeedbackResult result = feedbackProvider.generate(prompt);

            completionService.complete(job, result);
        } catch (InterviewFeedbackException exception) {
            handleFailure(job, exception);
        }
    }

    private void handleFailure(ClaimedFinalFeedbackJob job, InterviewFeedbackException exception
    ) {
        String errorCode = "INTERVIEW_FEEDBACK_" + exception.getFailure().name();

        switch (exception.getFailure()) {
            case RATE_LIMITED, TEMPORARY_FAILURE, INVALID_RESPONSE, UNKNOWN -> retry(job, errorCode, exception);

            case AUTHENTICATION_FAILED -> fail(job, errorCode, exception);
        }
    }

    private void retry(ClaimedFinalFeedbackJob job, String errorCode, RuntimeException exception) {
        OffsetDateTime nextAttemptAt = OffsetDateTime.now(ZoneOffset.UTC).plus(calculateRetryDelay(job.attemptCount()));

        boolean updated = jobControl.retry(
                job.processingJobId(),
                job.attemptCount(),
                nextAttemptAt,
                errorCode,
                resolveErrorMessage(exception)
        );

        if (!updated) {
            log.info(
                    "처리 권한이 만료된 종합 피드백 재시도 요청을 무시합니다. "
                            + "processingJobId={}, attemptCount={}",
                    job.processingJobId(),
                    job.attemptCount()
            );
        }
    }

    private void fail(ClaimedFinalFeedbackJob job, String errorCode, RuntimeException exception) {
        boolean updated = jobControl.fail(
                job.processingJobId(),
                job.attemptCount(),
                errorCode,
                resolveErrorMessage(exception)
        );

        if (!updated) {
            log.info(
                    "처리 권한이 만료된 종합 피드백 실패 요청을 무시합니다. "
                            + "processingJobId={}, attemptCount={}",
                    job.processingJobId(),
                    job.attemptCount()
            );
        }
    }

    private Duration calculateRetryDelay(int attemptCount) {
        long multiplier = 1L << Math.max(0, attemptCount - 1);

        Duration calculated = INITIAL_RETRY_DELAY.multipliedBy(multiplier);

        return calculated.compareTo(MAX_RETRY_DELAY) > 0
                ? MAX_RETRY_DELAY
                : calculated;
    }

    private String resolveErrorMessage(RuntimeException exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return message;
    }
}
