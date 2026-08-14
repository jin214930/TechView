package com.ward.techview.interview.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.interview.dto.InterviewProgressResponse;
import com.ward.techview.interview.dto.InterviewProgressStatus;
import com.ward.techview.interview.entity.Answer;
import com.ward.techview.interview.entity.InterviewCoreQuestion;
import com.ward.techview.interview.entity.InterviewSession;
import com.ward.techview.interview.entity.InterviewTurn;
import com.ward.techview.interview.entity.InterviewTurnType;
import com.ward.techview.interview.repository.AnswerRepository;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import com.ward.techview.interview.repository.InterviewTurnRepository;
import com.ward.techview.interview.response.InterviewResponseCode;
import com.ward.techview.processing.api.analysis.AnswerProcessingQuery;
import com.ward.techview.processing.api.analysis.AnswerProcessingSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewProgressQueryServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final Long TURN_ID = 20L;
    private static final Long ANSWER_ID = 30L;

    @Mock
    private InterviewSessionRepository interviewSessionRepository;

    @Mock
    private InterviewTurnRepository interviewTurnRepository;

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private AnswerProcessingQuery answerProcessingQuery;

    @InjectMocks
    private InterviewProgressQueryService service;

    @Test
    @DisplayName("회원 소유의 면접 세션이 없으면 조회할 수 없다")
    void rejectsMissingOrForeignSession() {
        when(interviewSessionRepository.findByIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getProgress(MEMBER_ID, SESSION_ID)
        );

        assertSame(InterviewResponseCode.INTERVIEW_NOT_FOUND, exception.getResponseCode());
        verifyNoInteractions(interviewTurnRepository, answerRepository, answerProcessingQuery);
    }

    @Test
    @DisplayName("면접 세션이 완료됐으면 완료 상태를 반환한다")
    void returnsCompletedWhenSessionIsCompleted() {
        InterviewSession session = session();
        session.complete(now());
        prepareSession(session);

        InterviewProgressResponse result = service.getProgress(MEMBER_ID, SESSION_ID);

        assertEmptyState(result, InterviewProgressStatus.COMPLETED);
        verifyNoInteractions(interviewTurnRepository, answerRepository, answerProcessingQuery);
    }

    @Test
    @DisplayName("진행 중인 세션의 현재 턴이 없으면 잘못된 면접 상태로 처리한다")
    void rejectsMissingCurrentTurn() {
        InterviewSession session = session();
        prepareSession(session);
        when(interviewTurnRepository.findCurrentTurn(SESSION_ID, 1))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getProgress(MEMBER_ID, SESSION_ID)
        );

        assertSame(InterviewResponseCode.INTERVIEW_STATE_INVALID, exception.getResponseCode());
        verifyNoInteractions(answerRepository, answerProcessingQuery);
    }

    @Test
    @DisplayName("현재 턴에 답변이 없으면 질문 준비 완료 상태를 반환한다")
    void returnsTurnReadyWhenCurrentTurnHasNoAnswer() {
        InterviewSession session = session();
        InterviewTurn turn = coreTurn(session);
        prepareCurrentTurn(session, turn);
        when(answerRepository.findByInterviewTurnId(TURN_ID))
                .thenReturn(Optional.empty());

        InterviewProgressResponse result = service.getProgress(MEMBER_ID, SESSION_ID);

        assertTurnReady(result);
        verifyNoInteractions(answerProcessingQuery);
    }

    @Test
    @DisplayName("답변은 있지만 처리 작업이 없으면 동일 질문을 준비 완료 상태로 반환한다")
    void returnsTurnReadyWhenAnswerProcessingHasNotStarted() {
        prepareAnsweredTurn();
        when(answerProcessingQuery.getStatus(ANSWER_ID))
                .thenReturn(AnswerProcessingSnapshot.notStarted());

        InterviewProgressResponse result = service.getProgress(MEMBER_ID, SESSION_ID);

        assertTurnReady(result);
    }

    @Test
    @DisplayName("답변 처리 작업이 진행 중이면 처리 중 상태를 반환한다")
    void returnsProcessingWhenAnswerIsProcessing() {
        prepareAnsweredTurn();
        when(answerProcessingQuery.getStatus(ANSWER_ID))
                .thenReturn(AnswerProcessingSnapshot.processing());

        InterviewProgressResponse result = service.getProgress(MEMBER_ID, SESSION_ID);

        assertEmptyState(result, InterviewProgressStatus.PROCESSING);
    }

    @Test
    @DisplayName("처리 완료 상태와 이전 현재 턴을 함께 읽으면 안전하게 처리 중 상태를 반환한다")
    void returnsProcessingForTransientCompletedJobRace() {
        prepareAnsweredTurn();
        when(answerProcessingQuery.getStatus(ANSWER_ID))
                .thenReturn(AnswerProcessingSnapshot.completed());

        InterviewProgressResponse result = service.getProgress(MEMBER_ID, SESSION_ID);

        assertEmptyState(result, InterviewProgressStatus.PROCESSING);
    }

    @Test
    @DisplayName("답변 처리 작업이 실패하면 실패 코드와 공통 안내 문구를 반환한다")
    void returnsFailedWhenAnswerProcessingFailed() {
        prepareAnsweredTurn();
        when(answerProcessingQuery.getStatus(ANSWER_ID))
                .thenReturn(AnswerProcessingSnapshot.failed("ANSWER_ANALYSIS_INVALID_RESPONSE"));

        InterviewProgressResponse result = service.getProgress(MEMBER_ID, SESSION_ID);

        assertAll(
                () -> assertEquals(SESSION_ID, result.sessionId()),
                () -> assertEquals(InterviewProgressStatus.FAILED, result.status()),
                () -> assertEquals("ANSWER_ANALYSIS_INVALID_RESPONSE", result.errorCode()),
                () -> assertEquals("답변 처리 중 오류가 발생했습니다.", result.errorMessage()),
                () -> assertNull(result.turnId()),
                () -> assertNull(result.question())
        );
    }

    private void prepareSession(InterviewSession session) {
        when(interviewSessionRepository.findByIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));
    }

    private void prepareCurrentTurn(InterviewSession session, InterviewTurn turn) {
        prepareSession(session);
        when(interviewTurnRepository.findCurrentTurn(SESSION_ID, session.getCurrentTurnOrder()))
                .thenReturn(Optional.of(turn));
    }

    private void prepareAnsweredTurn() {
        InterviewSession session = session();
        InterviewTurn turn = coreTurn(session);
        Answer answer = Answer.create(turn, "audio.webm", "audio/webm", 1_000);
        ReflectionTestUtils.setField(answer, "id", ANSWER_ID);
        prepareCurrentTurn(session, turn);
        when(answerRepository.findByInterviewTurnId(TURN_ID))
                .thenReturn(Optional.of(answer));
    }

    private static InterviewSession session() {
        InterviewSession session = InterviewSession.create(MEMBER_ID, now().minusMinutes(10));
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        return session;
    }

    private static InterviewTurn coreTurn(InterviewSession session) {
        InterviewCoreQuestion question = InterviewCoreQuestion.create(
                session,
                100L,
                "의존성 주입을 설명해 주세요.",
                "SPRING",
                1
        );
        InterviewTurn turn = InterviewTurn.createCore(session, 1, question);
        ReflectionTestUtils.setField(turn, "id", TURN_ID);
        return turn;
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private static void assertTurnReady(InterviewProgressResponse result) {
        assertAll(
                () -> assertEquals(SESSION_ID, result.sessionId()),
                () -> assertEquals(InterviewProgressStatus.TURN_READY, result.status()),
                () -> assertEquals(TURN_ID, result.turnId()),
                () -> assertEquals(1, result.turnOrder()),
                () -> assertEquals(InterviewTurnType.CORE, result.turnType()),
                () -> assertEquals("의존성 주입을 설명해 주세요.", result.question()),
                () -> assertNull(result.errorCode()),
                () -> assertNull(result.errorMessage())
        );
    }

    private static void assertEmptyState(
            InterviewProgressResponse result,
            InterviewProgressStatus status
    ) {
        assertAll(
                () -> assertEquals(SESSION_ID, result.sessionId()),
                () -> assertEquals(status, result.status()),
                () -> assertNull(result.turnId()),
                () -> assertNull(result.turnOrder()),
                () -> assertNull(result.turnType()),
                () -> assertNull(result.question()),
                () -> assertNull(result.errorCode()),
                () -> assertNull(result.errorMessage())
        );
    }
}
