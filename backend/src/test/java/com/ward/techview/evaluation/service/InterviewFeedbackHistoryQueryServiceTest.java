package com.ward.techview.evaluation.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.common.response.PageResponse;
import com.ward.techview.evaluation.dto.InterviewFeedbackStatus;
import com.ward.techview.evaluation.dto.InterviewHistoryItemResponse;
import com.ward.techview.evaluation.repository.InterviewFeedbackRepository;
import com.ward.techview.evaluation.response.EvaluationResponseCode;
import com.ward.techview.interview.api.CompletedInterviewSummary;
import com.ward.techview.interview.api.InterviewHistoryQueryApi;
import com.ward.techview.processing.api.FinalFeedbackProcessingQuery;
import com.ward.techview.processing.api.FinalFeedbackProcessingSnapshot;
import com.ward.techview.processing.api.FinalFeedbackProcessingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class InterviewFeedbackHistoryQueryServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private InterviewHistoryQueryApi interviewHistoryQueryApi;

    @Mock
    private InterviewFeedbackRepository interviewFeedbackRepository;

    @Mock
    private FinalFeedbackProcessingQuery processingQuery;

    @InjectMocks
    private InterviewFeedbackHistoryQueryService service;

    @Test
    @DisplayName("완료된 면접 기록에 피드백 완료·처리 중·실패 상태를 결합한다")
    void combinesFeedbackStatusesWithCompletedInterviews() {
        Page<CompletedInterviewSummary> interviews = interviewPage(
                interview(10L),
                interview(20L),
                interview(30L)
        );
        when(interviewHistoryQueryApi.getCompletedInterviews(MEMBER_ID, 0, 10))
                .thenReturn(interviews);
        when(interviewFeedbackRepository.findExistingInterviewSessionIds(Set.of(10L, 20L, 30L)))
                .thenReturn(Set.of(10L));
        when(processingQuery.getStatuses(Set.of(10L, 20L, 30L)))
                .thenReturn(Map.of(
                        20L,
                        snapshot(FinalFeedbackProcessingStatus.PROCESSING),
                        30L,
                        snapshot(FinalFeedbackProcessingStatus.FAILED)
                ));

        PageResponse<InterviewHistoryItemResponse> response =
                service.getCompletedInterviews(MEMBER_ID, 0, 10);

        assertAll(
                () -> assertEquals(3, response.items().size()),
                () -> assertEquals(InterviewFeedbackStatus.COMPLETED, response.items().get(0).feedbackStatus()),
                () -> assertEquals(InterviewFeedbackStatus.PROCESSING, response.items().get(1).feedbackStatus()),
                () -> assertEquals(InterviewFeedbackStatus.FAILED, response.items().get(2).feedbackStatus()),
                () -> assertEquals(0, response.page()),
                () -> assertEquals(10, response.size()),
                () -> assertEquals(3, response.totalElements()),
                () -> assertEquals(1, response.totalPages()),
                () -> assertEquals(false, response.hasNext())
        );

        InOrder order = inOrder(processingQuery, interviewFeedbackRepository);
        order.verify(processingQuery).getStatuses(Set.of(10L, 20L, 30L));
        order.verify(interviewFeedbackRepository)
                .findExistingInterviewSessionIds(Set.of(10L, 20L, 30L));
    }

    @Test
    @DisplayName("완료된 면접 기록이 없으면 피드백과 작업 상태를 조회하지 않는다")
    void returnsEmptyPageWithoutQueryingFeedbackStatus() {
        Page<CompletedInterviewSummary> interviews = Page.empty(PageRequest.of(1, 10));
        when(interviewHistoryQueryApi.getCompletedInterviews(MEMBER_ID, 1, 10))
                .thenReturn(interviews);

        PageResponse<InterviewHistoryItemResponse> response =
                service.getCompletedInterviews(MEMBER_ID, 1, 10);

        assertAll(
                () -> assertEquals(List.of(), response.items()),
                () -> assertEquals(1, response.page()),
                () -> assertEquals(10, response.size()),
                () -> assertEquals(0, response.totalElements())
        );
        verifyNoInteractions(interviewFeedbackRepository, processingQuery);
    }

    @ParameterizedTest
    @EnumSource(
            value = FinalFeedbackProcessingStatus.class,
            names = "SUCCEEDED"
    )
    @DisplayName("피드백 데이터와 작업 상태가 일치하지 않으면 상태 오류로 처리한다")
    void rejectsInconsistentFeedbackState(FinalFeedbackProcessingStatus status) {
        Page<CompletedInterviewSummary> interviews = interviewPage(interview(10L));
        when(interviewHistoryQueryApi.getCompletedInterviews(MEMBER_ID, 0, 10))
                .thenReturn(interviews);
        when(interviewFeedbackRepository.findExistingInterviewSessionIds(Set.of(10L)))
                .thenReturn(Set.of());
        when(processingQuery.getStatuses(Set.of(10L)))
                .thenReturn(Map.of(10L, snapshot(status)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getCompletedInterviews(MEMBER_ID, 0, 10)
        );

        assertSame(
                EvaluationResponseCode.INTERVIEW_FEEDBACK_STATE_INVALID,
                exception.getResponseCode()
        );
    }

    @Test
    @DisplayName("피드백 작업이 없는 레거시 면접은 실패 상태로 반환한다")
    void returnsFailedForLegacyInterviewWithoutFeedbackJob() {
        Page<CompletedInterviewSummary> interviews = interviewPage(interview(10L));
        when(interviewHistoryQueryApi.getCompletedInterviews(MEMBER_ID, 0, 10))
                .thenReturn(interviews);
        when(processingQuery.getStatuses(Set.of(10L)))
                .thenReturn(Map.of(
                        10L,
                        snapshot(FinalFeedbackProcessingStatus.NOT_STARTED)
                ));
        when(interviewFeedbackRepository.findExistingInterviewSessionIds(Set.of(10L)))
                .thenReturn(Set.of());

        PageResponse<InterviewHistoryItemResponse> response =
                service.getCompletedInterviews(MEMBER_ID, 0, 10);

        assertEquals(
                InterviewFeedbackStatus.FAILED,
                response.items().getFirst().feedbackStatus()
        );
    }

    @Test
    @DisplayName("작업 상태 조회 결과에 면접 세션이 없으면 상태 오류로 처리한다")
    void rejectsMissingProcessingStatus() {
        Page<CompletedInterviewSummary> interviews = interviewPage(interview(10L));
        when(interviewHistoryQueryApi.getCompletedInterviews(MEMBER_ID, 0, 10))
                .thenReturn(interviews);
        when(interviewFeedbackRepository.findExistingInterviewSessionIds(Set.of(10L)))
                .thenReturn(Set.of());
        when(processingQuery.getStatuses(Set.of(10L)))
                .thenReturn(Map.of());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getCompletedInterviews(MEMBER_ID, 0, 10)
        );

        assertSame(
                EvaluationResponseCode.INTERVIEW_FEEDBACK_STATE_INVALID,
                exception.getResponseCode()
        );
    }

    @Test
    @DisplayName("회원 ID가 없으면 하위 모듈을 호출하지 않는다")
    void rejectsNullMemberId() {
        assertThrows(
                NullPointerException.class,
                () -> service.getCompletedInterviews(null, 0, 10)
        );

        verifyNoInteractions(
                interviewHistoryQueryApi,
                interviewFeedbackRepository,
                processingQuery
        );
    }

    private static Page<CompletedInterviewSummary> interviewPage(
            CompletedInterviewSummary... interviews
    ) {
        return new PageImpl<>(
                List.of(interviews),
                PageRequest.of(0, 10),
                interviews.length
        );
    }

    private static CompletedInterviewSummary interview(Long sessionId) {
        OffsetDateTime startedAt = OffsetDateTime.of(
                2026,
                8,
                5,
                10,
                0,
                0,
                0,
                ZoneOffset.UTC
        ).plusDays(sessionId);

        return new CompletedInterviewSummary(
                sessionId,
                startedAt,
                startedAt.plusMinutes(15)
        );
    }

    private static FinalFeedbackProcessingSnapshot snapshot(
            FinalFeedbackProcessingStatus status
    ) {
        return new FinalFeedbackProcessingSnapshot(status);
    }
}
