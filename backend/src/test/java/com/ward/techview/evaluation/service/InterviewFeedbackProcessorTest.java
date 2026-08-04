package com.ward.techview.evaluation.service;

import com.ward.techview.evaluation.api.CategoryFeedback;
import com.ward.techview.evaluation.api.InterviewFeedbackException;
import com.ward.techview.evaluation.api.InterviewFeedbackFailure;
import com.ward.techview.evaluation.api.InterviewFeedbackPrompt;
import com.ward.techview.evaluation.api.InterviewFeedbackProvider;
import com.ward.techview.evaluation.api.InterviewFeedbackResult;
import com.ward.techview.evaluation.api.InterviewFeedbackTurnPrompt;
import com.ward.techview.processing.api.ClaimedFinalFeedbackJob;
import com.ward.techview.processing.api.FinalFeedbackJobControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewFeedbackProcessorTest {

    private static final Long PROCESSING_JOB_ID = 100L;
    private static final ClaimedFinalFeedbackJob JOB =
            new ClaimedFinalFeedbackJob(PROCESSING_JOB_ID, 10L, 1);

    @Mock
    private FinalFeedbackJobControl jobControl;

    @Mock
    private InterviewFeedbackPromptService promptService;

    @Mock
    private InterviewFeedbackProvider feedbackProvider;

    @Mock
    private InterviewFeedbackCompletionService completionService;

    @InjectMocks
    private InterviewFeedbackProcessor processor;

    @Test
    @DisplayName("선점할 수 없는 종합 피드백 작업은 처리하지 않는다")
    void doesNothingWhenJobCannotBeClaimed() {
        when(jobControl.claim(PROCESSING_JOB_ID)).thenReturn(Optional.empty());

        processor.process(PROCESSING_JOB_ID);

        verify(jobControl).claim(PROCESSING_JOB_ID);
        verifyNoInteractions(promptService, feedbackProvider, completionService);
    }

    @Test
    @DisplayName("선점한 종합 피드백 작업을 완료한다")
    void processesClaimedJobSuccessfully() {
        InterviewFeedbackPrompt prompt = prompt();
        InterviewFeedbackResult result = result();
        prepare(prompt);
        when(feedbackProvider.generate(prompt)).thenReturn(result);

        processor.process(PROCESSING_JOB_ID);

        verify(completionService).complete(JOB, result);
    }

    @Test
    @DisplayName("일시적인 공급자 오류가 발생하면 작업을 재시도 대기로 변경한다")
    void retriesTemporaryProviderFailure() {
        InterviewFeedbackException exception = new InterviewFeedbackException(
                InterviewFeedbackFailure.TEMPORARY_FAILURE,
                "일시적인 오류입니다.",
                null
        );
        InterviewFeedbackPrompt prompt = prompt();
        prepare(prompt);
        when(feedbackProvider.generate(prompt)).thenThrow(exception);

        processor.process(PROCESSING_JOB_ID);

        verify(jobControl).retry(
                eq(PROCESSING_JOB_ID),
                eq(JOB.attemptCount()),
                any(OffsetDateTime.class),
                eq("INTERVIEW_FEEDBACK_TEMPORARY_FAILURE"),
                eq(exception.getMessage())
        );
        verify(completionService, never()).complete(any(), any());
    }

    @Test
    @DisplayName("인증 오류가 발생하면 재시도하지 않고 작업을 실패 처리한다")
    void failsAuthenticationErrorWithoutRetry() {
        InterviewFeedbackException exception = new InterviewFeedbackException(
                InterviewFeedbackFailure.AUTHENTICATION_FAILED,
                "인증에 실패했습니다.",
                null
        );
        InterviewFeedbackPrompt prompt = prompt();
        prepare(prompt);
        when(feedbackProvider.generate(prompt)).thenThrow(exception);

        processor.process(PROCESSING_JOB_ID);

        verify(jobControl).fail(
                PROCESSING_JOB_ID,
                JOB.attemptCount(),
                "INTERVIEW_FEEDBACK_AUTHENTICATION_FAILED",
                exception.getMessage()
        );
        verify(jobControl, never()).retry(any(), any(Integer.class), any(), any(), any());
        verify(completionService, never()).complete(any(), any());
    }

    private void prepare(InterviewFeedbackPrompt prompt) {
        when(jobControl.claim(PROCESSING_JOB_ID)).thenReturn(Optional.of(JOB));
        when(promptService.create(JOB.interviewSessionId())).thenReturn(prompt);
    }

    private static InterviewFeedbackPrompt prompt() {
        return new InterviewFeedbackPrompt(
                JOB.interviewSessionId(),
                List.of(
                        new InterviewFeedbackTurnPrompt(
                                20L,
                                1,
                                "JAVA",
                                "질문",
                                null,
                                "답변",
                                "분석 요약",
                                List.of("강점"),
                                List.of("개선점")
                        )
                )
        );
    }

    private static InterviewFeedbackResult result() {
        return new InterviewFeedbackResult(
                "전체 총평",
                List.of("강점"),
                List.of("개선점"),
                List.of(
                        categoryFeedback("JAVA"),
                        categoryFeedback("SPRING"),
                        categoryFeedback("DB"),
                        categoryFeedback("OS"),
                        categoryFeedback("NETWORK")
                ),
                List.of("추천 주제"),
                "model",
                "v1"
        );
    }

    private static CategoryFeedback categoryFeedback(String category) {
        return new CategoryFeedback(category, "평가", "추천");
    }
}
