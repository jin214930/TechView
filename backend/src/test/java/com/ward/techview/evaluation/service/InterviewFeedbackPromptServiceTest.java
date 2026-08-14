package com.ward.techview.evaluation.service;

import com.ward.techview.evaluation.api.analysis.FollowUpDecision;
import com.ward.techview.evaluation.api.feedback.InterviewFeedbackPrompt;
import com.ward.techview.evaluation.entity.AnswerAnalysis;
import com.ward.techview.evaluation.repository.AnswerAnalysisRepository;
import com.ward.techview.interview.api.feedback.InterviewEvaluationContextApi;
import com.ward.techview.interview.api.feedback.InterviewFeedbackContext;
import com.ward.techview.interview.api.feedback.InterviewFeedbackTurnContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewFeedbackPromptServiceTest {

    private static final Long SESSION_ID = 10L;

    @Mock
    private InterviewEvaluationContextApi interviewEvaluationContextApi;

    @Mock
    private AnswerAnalysisRepository answerAnalysisRepository;

    @InjectMocks
    private InterviewFeedbackPromptService promptService;

    @Test
    @DisplayName("면접 답변과 분석 결과를 종합 피드백 프롬프트로 조립한다")
    void createsPromptFromInterviewContextAndAnswerAnalyses() {
        InterviewFeedbackContext context = context();
        when(interviewEvaluationContextApi.getFinalFeedbackContext(SESSION_ID)).thenReturn(context);
        when(answerAnalysisRepository.findAllByAnswerIdIn(List.of(100L, 101L)))
                .thenReturn(List.of(analysis(100L, "첫 번째 분석"), analysis(101L, "두 번째 분석")));

        InterviewFeedbackPrompt prompt = promptService.create(SESSION_ID);

        assertAll(
                () -> assertEquals(SESSION_ID, prompt.interviewSessionId()),
                () -> assertEquals(2, prompt.turns().size()),
                () -> assertEquals("첫 번째 분석", prompt.turns().get(0).analysisSummary()),
                () -> assertEquals("꼬리 질문", prompt.turns().get(1).followUpQuestion()),
                () -> assertEquals("두 번째 분석", prompt.turns().get(1).analysisSummary())
        );
    }

    @Test
    @DisplayName("답변 분석 결과가 하나라도 없으면 프롬프트 생성을 거부한다")
    void rejectsPromptWhenAnswerAnalysisIsMissing() {
        when(interviewEvaluationContextApi.getFinalFeedbackContext(SESSION_ID)).thenReturn(context());
        when(answerAnalysisRepository.findAllByAnswerIdIn(List.of(100L, 101L)))
                .thenReturn(List.of(analysis(100L, "첫 번째 분석")));

        assertThrows(
                IllegalStateException.class,
                () -> promptService.create(SESSION_ID)
        );
    }

    private static InterviewFeedbackContext context() {
        return new InterviewFeedbackContext(
                SESSION_ID,
                List.of(
                        new InterviewFeedbackTurnContext(
                                100L,
                                1,
                                "JAVA",
                                "핵심 질문",
                                null,
                                "첫 번째 답변"
                        ),
                        new InterviewFeedbackTurnContext(
                                101L,
                                2,
                                "JAVA",
                                "핵심 질문",
                                "꼬리 질문",
                                "두 번째 답변"
                        )
                )
        );
    }

    private static AnswerAnalysis analysis(Long answerId, String summary) {
        return AnswerAnalysis.create(
                answerId,
                summary,
                List.of("강점"),
                List.of("개선점"),
                FollowUpDecision.SKIP,
                "충분한 답변입니다.",
                "model",
                "v1"
        );
    }
}
