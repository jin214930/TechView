package com.ward.techview.evaluation.service;

import com.ward.techview.evaluation.api.AnswerAnalysisException;
import com.ward.techview.evaluation.api.AnswerAnalysisFailure;
import com.ward.techview.evaluation.api.AnswerAnalysisProvider;
import com.ward.techview.evaluation.api.AnswerAnalysisResult;
import com.ward.techview.evaluation.api.FollowUpDecision;
import com.ward.techview.interview.api.AnswerAnalysisContext;
import com.ward.techview.interview.api.InterviewAnswerApi;
import com.ward.techview.processing.api.AnswerAnalysisJobControl;
import com.ward.techview.processing.api.ClaimedAnswerAnalysisJob;
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
class AnswerAnalysisProcessorTest {

    private static final Long PROCESSING_JOB_ID = 100L;
    private static final ClaimedAnswerAnalysisJob JOB =
            new ClaimedAnswerAnalysisJob(PROCESSING_JOB_ID, 10L, 20L, 1);

    @Mock
    private AnswerAnalysisJobControl jobControl;

    @Mock
    private InterviewAnswerApi interviewAnswerApi;

    @Mock
    private AnswerAnalysisProvider analysisProvider;

    @Mock
    private AnswerAnalysisCompletionService completionService;

    @InjectMocks
    private AnswerAnalysisProcessor processor;

    @Test
    @DisplayName("선점할 수 없는 작업은 처리하지 않는다")
    void doesNothingWhenJobCannotBeClaimed() {
        when(jobControl.claim(PROCESSING_JOB_ID)).thenReturn(Optional.empty());

        processor.process(PROCESSING_JOB_ID);

        verify(jobControl).claim(PROCESSING_JOB_ID);
        verifyNoInteractions(interviewAnswerApi, analysisProvider, completionService);
    }

    @Test
    @DisplayName("선점한 답변 분석 작업을 완료한다")
    void processesClaimedJobSuccessfully() {
        AnswerAnalysisContext context = coreContext();
        AnswerAnalysisResult result = skipResult();
        prepare(context);
        when(analysisProvider.analyze(any())).thenReturn(result);

        processor.process(PROCESSING_JOB_ID);

        verify(completionService).complete(JOB, result);
    }

    @Test
    @DisplayName("꼬리 질문 답변에서 추가 꼬리 질문을 요청하면 작업을 재시도한다")
    void retriesWhenFollowUpAnswerRequestsAnotherFollowUp() {
        AnswerAnalysisContext context = followUpContext();
        AnswerAnalysisResult invalidResult = askResult();
        prepare(context);
        when(analysisProvider.analyze(any())).thenReturn(invalidResult);

        processor.process(PROCESSING_JOB_ID);

        verify(jobControl).retry(
                eq(PROCESSING_JOB_ID),
                eq(JOB.attemptCount()),
                any(OffsetDateTime.class),
                eq("ANSWER_ANALYSIS_INVALID_RESPONSE"),
                eq("꼬리 질문 답변에는 추가 꼬리 질문을 생성할 수 없습니다.")
        );
        verify(completionService, never()).complete(any(), any());
    }

    @Test
    @DisplayName("인증 실패가 발생하면 재시도하지 않고 작업을 실패 처리한다")
    void failsAuthenticationErrorWithoutRetry() {
        AnswerAnalysisException exception = new AnswerAnalysisException(
                AnswerAnalysisFailure.AUTHENTICATION_FAILED,
                "인증에 실패했습니다.",
                null
        );
        prepare(coreContext());
        when(analysisProvider.analyze(any())).thenThrow(exception);

        processor.process(PROCESSING_JOB_ID);

        verify(jobControl).fail(
                PROCESSING_JOB_ID,
                JOB.attemptCount(),
                "ANSWER_ANALYSIS_AUTHENTICATION_FAILED",
                exception.getMessage()
        );
        verify(jobControl, never()).retry(any(), any(Integer.class), any(), any(), any());
        verify(completionService, never()).complete(any(), any());
    }

    private void prepare(AnswerAnalysisContext context) {
        when(jobControl.claim(PROCESSING_JOB_ID)).thenReturn(Optional.of(JOB));
        when(interviewAnswerApi.getAnalysisContext(JOB.interviewSessionId(), JOB.answerId()))
                .thenReturn(context);
    }

    private static AnswerAnalysisContext coreContext() {
        return new AnswerAnalysisContext(10L, 20L, "JAVA", "핵심 질문", null, "답변");
    }

    private static AnswerAnalysisContext followUpContext() {
        return new AnswerAnalysisContext(10L, 20L, "JAVA", "핵심 질문", "꼬리 질문", "답변");
    }

    private static AnswerAnalysisResult skipResult() {
        return new AnswerAnalysisResult(
                "요약",
                List.of("강점"),
                List.of("개선점"),
                FollowUpDecision.SKIP,
                "충분한 답변입니다.",
                null,
                "model",
                "v1"
        );
    }

    private static AnswerAnalysisResult askResult() {
        return new AnswerAnalysisResult(
                "요약",
                List.of("강점"),
                List.of("개선점"),
                FollowUpDecision.ASK,
                "추가 확인이 필요합니다.",
                "추가로 설명해 주세요.",
                "model",
                "v1"
        );
    }
}
