package com.ward.techview.evaluation.service;

import com.ward.techview.evaluation.api.feedback.CategoryFeedback;
import com.ward.techview.evaluation.api.feedback.InterviewFeedbackResult;
import com.ward.techview.evaluation.entity.InterviewFeedback;
import com.ward.techview.evaluation.repository.InterviewFeedbackRepository;
import com.ward.techview.processing.api.feedback.ClaimedFinalFeedbackJob;
import com.ward.techview.processing.api.feedback.FinalFeedbackJobControl;
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
class InterviewFeedbackCompletionServiceTest {

    private static final ClaimedFinalFeedbackJob JOB =
            new ClaimedFinalFeedbackJob(100L, 10L, 1);

    @Mock
    private InterviewFeedbackRepository interviewFeedbackRepository;

    @Mock
    private FinalFeedbackJobControl jobControl;

    @InjectMocks
    private InterviewFeedbackCompletionService completionService;

    @Test
    @DisplayName("현재 작업 시도를 성공 처리하고 종합 피드백을 저장한다")
    void succeedsJobAndSavesFeedback() {
        InterviewFeedbackResult result = result();
        when(jobControl.succeed(JOB.processingJobId(), JOB.attemptCount())).thenReturn(true);

        completionService.complete(JOB, result);

        ArgumentCaptor<InterviewFeedback> feedbackCaptor =
                ArgumentCaptor.forClass(InterviewFeedback.class);
        InOrder order = inOrder(jobControl, interviewFeedbackRepository);
        order.verify(jobControl).succeed(JOB.processingJobId(), JOB.attemptCount());
        order.verify(interviewFeedbackRepository).save(feedbackCaptor.capture());

        InterviewFeedback feedback = feedbackCaptor.getValue();
        assertAll(
                () -> assertEquals(JOB.interviewSessionId(), feedback.getInterviewSessionId()),
                () -> assertEquals(result.overallSummary(), feedback.getOverallSummary()),
                () -> assertEquals(result.categoryFeedbacks(), feedback.getCategoryFeedbacks()),
                () -> assertEquals(result.model(), feedback.getModel()),
                () -> assertEquals(result.promptVersion(), feedback.getPromptVersion())
        );
    }

    @Test
    @DisplayName("작업 처리 권한이 만료되면 종합 피드백을 저장하지 않는다")
    void rejectsCompletionWhenJobAttemptIsNoLongerCurrent() {
        when(jobControl.succeed(JOB.processingJobId(), JOB.attemptCount())).thenReturn(false);

        assertThrows(
                IllegalStateException.class,
                () -> completionService.complete(JOB, result())
        );

        verify(interviewFeedbackRepository, never()).save(org.mockito.ArgumentMatchers.any());
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
