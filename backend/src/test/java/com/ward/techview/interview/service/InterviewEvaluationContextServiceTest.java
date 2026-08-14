package com.ward.techview.interview.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.interview.api.answer.AnswerAnalysisContext;
import com.ward.techview.interview.api.feedback.InterviewFeedbackContext;
import com.ward.techview.interview.api.feedback.InterviewFeedbackTurnContext;
import com.ward.techview.interview.entity.Answer;
import com.ward.techview.interview.entity.FollowUpQuestion;
import com.ward.techview.interview.entity.InterviewCoreQuestion;
import com.ward.techview.interview.entity.InterviewSession;
import com.ward.techview.interview.entity.InterviewStatus;
import com.ward.techview.interview.entity.InterviewTurn;
import com.ward.techview.interview.repository.AnswerRepository;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import com.ward.techview.interview.response.InterviewResponseCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewEvaluationContextServiceTest {

    private static final Long SESSION_ID = 10L;
    private static final Long CORE_ANSWER_ID = 100L;
    private static final Long FOLLOW_UP_ANSWER_ID = 200L;
    private static final String OBJECT_KEY = "interviews/10/audio.webm";
    private static final String CONTENT_TYPE = "audio/webm";
    private static final int DURATION_MS = 42_000;

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private InterviewSessionRepository interviewSessionRepository;

    @InjectMocks
    private InterviewEvaluationContextService service;

    @Test
    @DisplayName("전사된 핵심 질문 답변의 분석 문맥을 반환한다")
    void returnsCoreAnswerAnalysisContext() {
        InterviewSession session = session(InterviewStatus.IN_PROGRESS, 1);
        Answer answer = transcribedAnswer(
                coreTurn(session, 1),
                CORE_ANSWER_ID,
                "다형성은 하나의 타입으로 여러 구현을 다루는 특성입니다."
        );
        when(answerRepository.findAnalysisTarget(SESSION_ID, CORE_ANSWER_ID))
                .thenReturn(Optional.of(answer));

        AnswerAnalysisContext result = service.getAnalysisContext(
                SESSION_ID,
                CORE_ANSWER_ID
        );

        assertAll(
                () -> assertEquals(SESSION_ID, result.interviewSessionId()),
                () -> assertEquals(CORE_ANSWER_ID, result.answerId()),
                () -> assertEquals("JAVA", result.category()),
                () -> assertEquals("자바의 다형성을 설명해 주세요.", result.coreQuestion()),
                () -> assertNull(result.followUpQuestion()),
                () -> assertEquals(
                        "다형성은 하나의 타입으로 여러 구현을 다루는 특성입니다.",
                        result.transcript()
                ),
                () -> assertTrue(result.followUpAllowed())
        );
    }

    @Test
    @DisplayName("전사된 꼬리 질문 답변에 원본 핵심 질문을 포함한 분석 문맥을 반환한다")
    void returnsFollowUpAnswerAnalysisContext() {
        InterviewSession session = session(InterviewStatus.IN_PROGRESS, 2);
        Answer answer = transcribedAnswer(
                followUpTurn(session, 2),
                FOLLOW_UP_ANSWER_ID,
                "오버라이딩을 통해 실행 시점에 실제 구현이 선택됩니다."
        );
        when(answerRepository.findAnalysisTarget(SESSION_ID, FOLLOW_UP_ANSWER_ID))
                .thenReturn(Optional.of(answer));

        AnswerAnalysisContext result = service.getAnalysisContext(
                SESSION_ID,
                FOLLOW_UP_ANSWER_ID
        );

        assertAll(
                () -> assertEquals("JAVA", result.category()),
                () -> assertEquals("자바의 다형성을 설명해 주세요.", result.coreQuestion()),
                () -> assertEquals(
                        "다형성이 실제로 동작하는 예를 설명해 주세요.",
                        result.followUpQuestion()
                ),
                () -> assertEquals(
                        "오버라이딩을 통해 실행 시점에 실제 구현이 선택됩니다.",
                        result.transcript()
                ),
                () -> assertFalse(result.followUpAllowed())
        );
    }

    @Test
    @DisplayName("전사되지 않은 답변은 분석 문맥을 조회할 수 없다")
    void rejectsAnalysisContextWhenAnswerIsNotTranscribed() {
        InterviewSession session = session(InterviewStatus.IN_PROGRESS, 1);
        Answer answer = answer(coreTurn(session, 1), CORE_ANSWER_ID);
        when(answerRepository.findAnalysisTarget(SESSION_ID, CORE_ANSWER_ID))
                .thenReturn(Optional.of(answer));

        assertThrows(
                IllegalStateException.class,
                () -> service.getAnalysisContext(SESSION_ID, CORE_ANSWER_ID)
        );
    }

    @Test
    @DisplayName("면접 세션에 속한 답변이 없으면 분석 문맥을 조회할 수 없다")
    void rejectsAnalysisContextWhenAnswerDoesNotExistInSession() {
        when(answerRepository.findAnalysisTarget(SESSION_ID, CORE_ANSWER_ID))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getAnalysisContext(SESSION_ID, CORE_ANSWER_ID)
        );

        assertSame(InterviewResponseCode.ANSWER_NOT_FOUND, exception.getResponseCode());
    }

    @Test
    @DisplayName("완료된 면접의 모든 답변을 턴 순서의 종합 피드백 문맥으로 반환한다")
    void returnsFinalFeedbackContext() {
        InterviewSession session = session(InterviewStatus.COMPLETED, 2);
        Answer coreAnswer = transcribedAnswer(
                coreTurn(session, 1),
                CORE_ANSWER_ID,
                "다형성 답변"
        );
        Answer followUpAnswer = transcribedAnswer(
                followUpTurn(session, 2),
                FOLLOW_UP_ANSWER_ID,
                "다형성 예시 답변"
        );
        when(interviewSessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(session));
        when(answerRepository.findAllFeedbackTargets(SESSION_ID))
                .thenReturn(List.of(coreAnswer, followUpAnswer));

        InterviewFeedbackContext result = service.getFinalFeedbackContext(SESSION_ID);

        InterviewFeedbackTurnContext coreTurn = result.turns().get(0);
        InterviewFeedbackTurnContext followUpTurn = result.turns().get(1);
        assertAll(
                () -> assertEquals(SESSION_ID, result.interviewSessionId()),
                () -> assertEquals(2, result.turns().size()),
                () -> assertEquals(CORE_ANSWER_ID, coreTurn.answerId()),
                () -> assertEquals(1, coreTurn.turnOrder()),
                () -> assertEquals("JAVA", coreTurn.category()),
                () -> assertEquals("자바의 다형성을 설명해 주세요.", coreTurn.coreQuestion()),
                () -> assertNull(coreTurn.followUpQuestion()),
                () -> assertEquals("다형성 답변", coreTurn.transcript()),
                () -> assertEquals(FOLLOW_UP_ANSWER_ID, followUpTurn.answerId()),
                () -> assertEquals(2, followUpTurn.turnOrder()),
                () -> assertEquals("JAVA", followUpTurn.category()),
                () -> assertEquals("자바의 다형성을 설명해 주세요.", followUpTurn.coreQuestion()),
                () -> assertEquals(
                        "다형성이 실제로 동작하는 예를 설명해 주세요.",
                        followUpTurn.followUpQuestion()
                ),
                () -> assertEquals("다형성 예시 답변", followUpTurn.transcript())
        );
    }

    @Test
    @DisplayName("완료되지 않은 면접은 종합 피드백 문맥을 조회할 수 없다")
    void rejectsFinalFeedbackContextWhenInterviewIsNotCompleted() {
        InterviewSession session = session(InterviewStatus.IN_PROGRESS, 1);
        when(interviewSessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(session));

        assertThrows(
                IllegalStateException.class,
                () -> service.getFinalFeedbackContext(SESSION_ID)
        );

        verify(answerRepository, never()).findAllFeedbackTargets(SESSION_ID);
    }

    @Test
    @DisplayName("면접 세션이 없으면 종합 피드백 문맥을 조회할 수 없다")
    void rejectsFinalFeedbackContextWhenInterviewDoesNotExist() {
        when(interviewSessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getFinalFeedbackContext(SESSION_ID)
        );

        assertSame(InterviewResponseCode.INTERVIEW_NOT_FOUND, exception.getResponseCode());
        verify(answerRepository, never()).findAllFeedbackTargets(SESSION_ID);
    }

    @Test
    @DisplayName("완료된 면접에 답변이 없으면 종합 피드백 문맥을 조회할 수 없다")
    void rejectsFinalFeedbackContextWhenAnswersAreEmpty() {
        InterviewSession session = session(InterviewStatus.COMPLETED, 1);
        when(interviewSessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(session));
        when(answerRepository.findAllFeedbackTargets(SESSION_ID))
                .thenReturn(List.of());

        assertThrows(
                IllegalStateException.class,
                () -> service.getFinalFeedbackContext(SESSION_ID)
        );
    }

    @Test
    @DisplayName("면접 턴보다 답변이 적으면 종합 피드백 문맥을 조회할 수 없다")
    void rejectsFinalFeedbackContextWhenAnswerIsMissing() {
        InterviewSession session = session(InterviewStatus.COMPLETED, 2);
        Answer answer = transcribedAnswer(
                coreTurn(session, 1),
                CORE_ANSWER_ID,
                "다형성 답변"
        );
        when(interviewSessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(session));
        when(answerRepository.findAllFeedbackTargets(SESSION_ID))
                .thenReturn(List.of(answer));

        assertThrows(
                IllegalStateException.class,
                () -> service.getFinalFeedbackContext(SESSION_ID)
        );
    }

    @Test
    @DisplayName("미전사 답변이 있으면 종합 피드백 문맥을 조회할 수 없다")
    void rejectsFinalFeedbackContextWhenAnswerIsNotTranscribed() {
        InterviewSession session = session(InterviewStatus.COMPLETED, 1);
        Answer answer = answer(coreTurn(session, 1), CORE_ANSWER_ID);
        when(interviewSessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(session));
        when(answerRepository.findAllFeedbackTargets(SESSION_ID))
                .thenReturn(List.of(answer));

        assertThrows(
                IllegalStateException.class,
                () -> service.getFinalFeedbackContext(SESSION_ID)
        );
    }

    private static InterviewSession session(
            InterviewStatus status,
            int currentTurnOrder
    ) {
        InterviewSession session = InterviewSession.create(
                1L,
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10)
        );
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        ReflectionTestUtils.setField(session, "status", status);
        ReflectionTestUtils.setField(session, "currentTurnOrder", currentTurnOrder);
        return session;
    }

    private static InterviewTurn coreTurn(
            InterviewSession session,
            int turnOrder
    ) {
        InterviewCoreQuestion coreQuestion = coreQuestion(session);
        InterviewTurn turn = InterviewTurn.createCore(
                session,
                turnOrder,
                coreQuestion
        );
        ReflectionTestUtils.setField(turn, "id", (long) turnOrder);
        return turn;
    }

    private static InterviewTurn followUpTurn(
            InterviewSession session,
            int turnOrder
    ) {
        InterviewCoreQuestion coreQuestion = coreQuestion(session);
        FollowUpQuestion followUpQuestion = FollowUpQuestion.create(
                coreQuestion,
                "다형성이 실제로 동작하는 예를 설명해 주세요."
        );
        ReflectionTestUtils.setField(followUpQuestion, "id", 30L);
        InterviewTurn turn = InterviewTurn.createFollowUp(
                session,
                turnOrder,
                followUpQuestion
        );
        ReflectionTestUtils.setField(turn, "id", (long) turnOrder);
        return turn;
    }

    private static InterviewCoreQuestion coreQuestion(InterviewSession session) {
        InterviewCoreQuestion coreQuestion = InterviewCoreQuestion.create(
                session,
                1L,
                "자바의 다형성을 설명해 주세요.",
                "JAVA",
                1
        );
        ReflectionTestUtils.setField(coreQuestion, "id", 20L);
        return coreQuestion;
    }

    private static Answer answer(InterviewTurn turn, Long answerId) {
        Answer answer = Answer.create(
                turn,
                OBJECT_KEY,
                CONTENT_TYPE,
                DURATION_MS
        );
        ReflectionTestUtils.setField(answer, "id", answerId);
        return answer;
    }

    private static Answer transcribedAnswer(
            InterviewTurn turn,
            Long answerId,
            String transcript
    ) {
        Answer answer = answer(turn, answerId);
        answer.completeTranscription(
                transcript,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        return answer;
    }
}
