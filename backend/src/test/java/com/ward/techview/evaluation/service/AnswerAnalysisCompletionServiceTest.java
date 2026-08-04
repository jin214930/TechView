package com.ward.techview.evaluation.service;

import com.ward.techview.evaluation.api.AnswerAnalysisResult;
import com.ward.techview.evaluation.api.FollowUpDecision;
import com.ward.techview.evaluation.entity.AnswerAnalysis;
import com.ward.techview.evaluation.repository.AnswerAnalysisRepository;
import com.ward.techview.interview.api.InterviewAdvanceCommand;
import com.ward.techview.interview.api.InterviewAdvanceResult;
import com.ward.techview.interview.api.InterviewProgressApi;
import com.ward.techview.processing.api.AnswerAnalysisJobControl;
import com.ward.techview.processing.api.ClaimedAnswerAnalysisJob;
import com.ward.techview.processing.api.ProcessingJobQueue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerAnalysisCompletionServiceTest {

    private static final ClaimedAnswerAnalysisJob JOB =
            new ClaimedAnswerAnalysisJob(100L, 10L, 20L, 1);
    private static final AnswerAnalysisResult RESULT = new AnswerAnalysisResult(
            "요약",
            List.of("강점"),
            List.of("개선점"),
            FollowUpDecision.ASK,
            "추가 확인이 필요합니다.",
            "추가로 설명해 주세요.",
            "model",
            "v1"
    );

    @Mock
    private AnswerAnalysisRepository answerAnalysisRepository;

    @Mock
    private InterviewProgressApi interviewProgressApi;

    @Mock
    private AnswerAnalysisJobControl jobControl;

    @Mock
    private ProcessingJobQueue processingJobQueue;

    @InjectMocks
    private AnswerAnalysisCompletionService completionService;

    @Test
    @DisplayName("분석 결과 저장과 면접 진행 후 작업을 성공 처리한다")
    void savesAnalysisAdvancesInterviewAndSucceedsJob() {
        when(interviewProgressApi.advanceAfterAnalysis(org.mockito.ArgumentMatchers.any()))
                .thenReturn(InterviewAdvanceResult.continued());
        when(jobControl.succeed(JOB.processingJobId(), JOB.attemptCount())).thenReturn(true);

        completionService.complete(JOB, RESULT);

        ArgumentCaptor<AnswerAnalysis> analysisCaptor = ArgumentCaptor.forClass(AnswerAnalysis.class);
        ArgumentCaptor<InterviewAdvanceCommand> commandCaptor =
                ArgumentCaptor.forClass(InterviewAdvanceCommand.class);
        InOrder order = inOrder(answerAnalysisRepository, interviewProgressApi, jobControl);
        order.verify(answerAnalysisRepository).save(analysisCaptor.capture());
        order.verify(interviewProgressApi).advanceAfterAnalysis(commandCaptor.capture());
        order.verify(jobControl).succeed(JOB.processingJobId(), JOB.attemptCount());
        verify(processingJobQueue, never()).createOrGetFinalFeedbackJob(JOB.interviewSessionId());

        assertAll(
                () -> assertEquals(JOB.answerId(), analysisCaptor.getValue().getAnswerId()),
                () -> assertEquals(RESULT.summary(), analysisCaptor.getValue().getSummary()),
                () -> assertEquals(JOB.interviewSessionId(), commandCaptor.getValue().interviewSessionId()),
                () -> assertEquals(JOB.answerId(), commandCaptor.getValue().answerId()),
                () -> assertEquals(RESULT.followUpQuestion(), commandCaptor.getValue().followUpQuestion())
        );
    }

    @Test
    @DisplayName("마지막 답변 분석으로 면접이 완료되면 종합 피드백 작업을 생성한다")
    void createsFinalFeedbackJobWhenInterviewIsCompleted() {
        when(interviewProgressApi.advanceAfterAnalysis(org.mockito.ArgumentMatchers.any()))
                .thenReturn(InterviewAdvanceResult.completed());
        when(jobControl.succeed(JOB.processingJobId(), JOB.attemptCount())).thenReturn(true);

        completionService.complete(JOB, RESULT);

        InOrder order = inOrder(
                answerAnalysisRepository,
                interviewProgressApi,
                processingJobQueue,
                jobControl
        );
        order.verify(answerAnalysisRepository).save(org.mockito.ArgumentMatchers.any(AnswerAnalysis.class));
        order.verify(interviewProgressApi).advanceAfterAnalysis(
                org.mockito.ArgumentMatchers.any(InterviewAdvanceCommand.class)
        );
        order.verify(processingJobQueue).createOrGetFinalFeedbackJob(JOB.interviewSessionId());
        order.verify(jobControl).succeed(JOB.processingJobId(), JOB.attemptCount());
    }

    @Test
    @DisplayName("작업 처리 권한이 만료되면 예외를 발생시킨다")
    void throwsWhenJobAttemptIsNoLongerCurrent() {
        when(interviewProgressApi.advanceAfterAnalysis(org.mockito.ArgumentMatchers.any()))
                .thenReturn(InterviewAdvanceResult.continued());
        when(jobControl.succeed(JOB.processingJobId(), JOB.attemptCount())).thenReturn(false);

        assertThrows(
                IllegalStateException.class,
                () -> completionService.complete(JOB, RESULT)
        );

        verify(answerAnalysisRepository).save(org.mockito.ArgumentMatchers.any(AnswerAnalysis.class));
        verify(interviewProgressApi).advanceAfterAnalysis(
                org.mockito.ArgumentMatchers.any(InterviewAdvanceCommand.class)
        );
    }
}
