package com.ward.techview.evaluation.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.evaluation.api.feedback.CategoryFeedback;
import com.ward.techview.evaluation.dto.InterviewFeedbackResponse;
import com.ward.techview.evaluation.dto.InterviewFeedbackStatus;
import com.ward.techview.evaluation.entity.InterviewFeedback;
import com.ward.techview.evaluation.repository.InterviewFeedbackRepository;
import com.ward.techview.evaluation.response.EvaluationResponseCode;
import com.ward.techview.interview.api.feedback.InterviewFeedbackAccessApi;
import com.ward.techview.processing.api.feedback.FinalFeedbackProcessingQuery;
import com.ward.techview.processing.api.feedback.FinalFeedbackProcessingSnapshot;
import com.ward.techview.processing.api.feedback.FinalFeedbackProcessingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewFeedbackQueryServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;

    @Mock
    private InterviewFeedbackAccessApi interviewFeedbackAccessApi;

    @Mock
    private InterviewFeedbackRepository interviewFeedbackRepository;

    @Mock
    private FinalFeedbackProcessingQuery processingQuery;

    @InjectMocks
    private InterviewFeedbackQueryService service;

    @Test
    @DisplayName("생성된 종합 피드백이 있으면 완료 응답을 반환한다")
    void returnsCompletedFeedbackWhenFeedbackExists() {
        InterviewFeedback feedback = feedback();
        when(processingQuery.getStatus(SESSION_ID))
                .thenReturn(new FinalFeedbackProcessingSnapshot(
                        FinalFeedbackProcessingStatus.SUCCEEDED
                ));
        when(interviewFeedbackRepository.findByInterviewSessionId(SESSION_ID))
                .thenReturn(Optional.of(feedback));

        InterviewFeedbackResponse response = service.getFeedback(MEMBER_ID, SESSION_ID);

        InOrder order = inOrder(
                interviewFeedbackAccessApi,
                processingQuery,
                interviewFeedbackRepository
        );
        order.verify(interviewFeedbackAccessApi).validateCompletedInterview(MEMBER_ID, SESSION_ID);
        order.verify(processingQuery).getStatus(SESSION_ID);
        order.verify(interviewFeedbackRepository).findByInterviewSessionId(SESSION_ID);
        assertAll(
                () -> assertEquals(InterviewFeedbackStatus.COMPLETED, response.status()),
                () -> assertEquals("전체 총평", response.feedback().overallSummary())
        );
    }

    @Test
    @DisplayName("종합 피드백 작업이 진행 중이면 처리 중 응답을 반환한다")
    void returnsProcessingWhenFeedbackJobIsProcessing() {
        prepareMissingFeedback(FinalFeedbackProcessingStatus.PROCESSING);

        InterviewFeedbackResponse response = service.getFeedback(MEMBER_ID, SESSION_ID);

        assertEquals(InterviewFeedbackStatus.PROCESSING, response.status());
    }

    @Test
    @DisplayName("종합 피드백 작업이 최종 실패하면 일반화된 실패 응답을 반환한다")
    void returnsFailedWhenFeedbackJobFailed() {
        prepareMissingFeedback(FinalFeedbackProcessingStatus.FAILED);

        InterviewFeedbackResponse response = service.getFeedback(MEMBER_ID, SESSION_ID);

        assertAll(
                () -> assertEquals(InterviewFeedbackStatus.FAILED, response.status()),
                () -> assertEquals("피드백을 제공할 수 없습니다.", response.errorMessage())
        );
    }

    @Test
    @DisplayName("피드백 작업이 없는 레거시 면접은 제공 불가 응답을 반환한다")
    void returnsFailedWhenFeedbackJobWasNotStarted() {
        prepareMissingFeedback(FinalFeedbackProcessingStatus.NOT_STARTED);

        InterviewFeedbackResponse response = service.getFeedback(MEMBER_ID, SESSION_ID);

        assertAll(
                () -> assertEquals(InterviewFeedbackStatus.FAILED, response.status()),
                () -> assertEquals("피드백을 제공할 수 없습니다.", response.errorMessage())
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = FinalFeedbackProcessingStatus.class,
            names = "SUCCEEDED"
    )
    @DisplayName("피드백 데이터와 작업 상태가 일치하지 않으면 상태 오류로 처리한다")
    void rejectsInconsistentFeedbackState(FinalFeedbackProcessingStatus status) {
        prepareMissingFeedback(status);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getFeedback(MEMBER_ID, SESSION_ID)
        );

        assertSame(
                EvaluationResponseCode.INTERVIEW_FEEDBACK_STATE_INVALID,
                exception.getResponseCode()
        );
    }

    @Test
    @DisplayName("면접 접근 검증에 실패하면 피드백과 작업 상태를 조회하지 않는다")
    void stopsWhenInterviewAccessValidationFails() {
        BusinessException expected = new BusinessException(
                EvaluationResponseCode.INTERVIEW_FEEDBACK_STATE_INVALID
        );
        org.mockito.Mockito.doThrow(expected)
                .when(interviewFeedbackAccessApi)
                .validateCompletedInterview(MEMBER_ID, SESSION_ID);

        BusinessException actual = assertThrows(
                BusinessException.class,
                () -> service.getFeedback(MEMBER_ID, SESSION_ID)
        );

        assertSame(expected, actual);
        verifyNoInteractions(interviewFeedbackRepository, processingQuery);
    }

    @Test
    @DisplayName("회원 ID 또는 면접 세션 ID가 없으면 하위 모듈을 호출하지 않는다")
    void rejectsNullIdentifiers() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> service.getFeedback(null, SESSION_ID)
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> service.getFeedback(MEMBER_ID, null)
                )
        );

        verifyNoInteractions(
                interviewFeedbackAccessApi,
                interviewFeedbackRepository,
                processingQuery
        );
    }

    private void prepareMissingFeedback(FinalFeedbackProcessingStatus status) {
        when(interviewFeedbackRepository.findByInterviewSessionId(SESSION_ID))
                .thenReturn(Optional.empty());
        when(processingQuery.getStatus(SESSION_ID))
                .thenReturn(new FinalFeedbackProcessingSnapshot(status));
    }

    private static InterviewFeedback feedback() {
        return InterviewFeedback.create(
                SESSION_ID,
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
