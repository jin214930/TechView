package com.ward.techview.interview.service;

import com.ward.techview.interview.api.InterviewAdvanceCommand;
import com.ward.techview.interview.api.InterviewAdvanceResult;
import com.ward.techview.interview.entity.Answer;
import com.ward.techview.interview.entity.FollowUpQuestion;
import com.ward.techview.interview.entity.InterviewCoreQuestion;
import com.ward.techview.interview.entity.InterviewSession;
import com.ward.techview.interview.entity.InterviewStatus;
import com.ward.techview.interview.entity.InterviewTurn;
import com.ward.techview.interview.entity.InterviewTurnType;
import com.ward.techview.interview.repository.AnswerRepository;
import com.ward.techview.interview.repository.FollowUpQuestionRepository;
import com.ward.techview.interview.repository.InterviewCoreQuestionRepository;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import com.ward.techview.interview.repository.InterviewTurnRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewProgressServiceTest {

    private static final Long SESSION_ID = 10L;
    private static final Long ANSWER_ID = 20L;
    private static final OffsetDateTime ANALYZED_AT =
            OffsetDateTime.of(2026, 8, 4, 12, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private InterviewSessionRepository interviewSessionRepository;

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private InterviewCoreQuestionRepository coreQuestionRepository;

    @Mock
    private FollowUpQuestionRepository followUpQuestionRepository;

    @Mock
    private InterviewTurnRepository interviewTurnRepository;

    @Mock
    private InterviewRetentionPolicy retentionPolicy;

    @InjectMocks
    private InterviewProgressService progressService;

    @BeforeEach
    void setUpRetentionPolicy() {
        lenient().when(retentionPolicy.isExpired(
                any(InterviewSession.class),
                any(OffsetDateTime.class)
        )).thenReturn(false);
    }

    @Test
    @DisplayName("핵심 질문 분석에서 꼬리 질문이 필요하면 다음 턴으로 생성한다")
    void createsFollowUpTurnWhenAnalysisRequestsFollowUp() {
        InterviewSession session = session();
        InterviewCoreQuestion coreQuestion = coreQuestion(session, 1);
        InterviewTurn turn = InterviewTurn.createCore(session, 1, coreQuestion);
        Answer answer = transcribedAnswer(turn);
        prepare(session, answer);

        InterviewAdvanceResult result = progressService.advanceAfterAnalysis(
                new InterviewAdvanceCommand(SESSION_ID, ANSWER_ID, "추가로 설명해 주세요.", ANALYZED_AT)
        );

        ArgumentCaptor<FollowUpQuestion> questionCaptor =
                ArgumentCaptor.forClass(FollowUpQuestion.class);
        ArgumentCaptor<InterviewTurn> turnCaptor = ArgumentCaptor.forClass(InterviewTurn.class);
        verify(followUpQuestionRepository).save(questionCaptor.capture());
        verify(interviewTurnRepository).save(turnCaptor.capture());

        assertAll(
                () -> assertEquals(false, result.interviewCompleted()),
                () -> assertEquals(2, session.getCurrentTurnOrder()),
                () -> assertEquals("추가로 설명해 주세요.", questionCaptor.getValue().getContent()),
                () -> assertEquals(InterviewTurnType.FOLLOW_UP, turnCaptor.getValue().getTurnType()),
                () -> assertEquals(2, turnCaptor.getValue().getTurnOrder()),
                () -> assertEquals(questionCaptor.getValue(), turnCaptor.getValue().getFollowUpQuestion())
        );
    }

    @Test
    @DisplayName("핵심 질문 분석에서 꼬리 질문이 없으면 다음 핵심 질문 턴을 생성한다")
    void createsNextCoreTurnWhenFollowUpIsSkipped() {
        InterviewSession session = session();
        InterviewCoreQuestion currentQuestion = coreQuestion(session, 1);
        InterviewCoreQuestion nextQuestion = coreQuestion(session, 2);
        Answer answer = transcribedAnswer(InterviewTurn.createCore(session, 1, currentQuestion));
        prepare(session, answer);
        when(coreQuestionRepository.findBySessionIdAndQuestionOrder(SESSION_ID, 2))
                .thenReturn(Optional.of(nextQuestion));

        InterviewAdvanceResult result = progressService.advanceAfterAnalysis(
                new InterviewAdvanceCommand(SESSION_ID, ANSWER_ID, null, ANALYZED_AT)
        );

        ArgumentCaptor<InterviewTurn> turnCaptor = ArgumentCaptor.forClass(InterviewTurn.class);
        verify(interviewTurnRepository).save(turnCaptor.capture());
        assertAll(
                () -> assertEquals(false, result.interviewCompleted()),
                () -> assertEquals(2, session.getCurrentTurnOrder()),
                () -> assertEquals(InterviewTurnType.CORE, turnCaptor.getValue().getTurnType()),
                () -> assertEquals(nextQuestion, turnCaptor.getValue().getInterviewCoreQuestion())
        );
        verify(followUpQuestionRepository, never()).save(any());
    }

    @Test
    @DisplayName("마지막 핵심 질문 분석이 끝나면 면접을 완료한다")
    void completesInterviewAfterLastCoreQuestion() {
        InterviewSession session = session();
        InterviewCoreQuestion lastQuestion = coreQuestion(session, 5);
        Answer answer = transcribedAnswer(InterviewTurn.createCore(session, 1, lastQuestion));
        prepare(session, answer);
        when(coreQuestionRepository.findBySessionIdAndQuestionOrder(SESSION_ID, 6))
                .thenReturn(Optional.empty());

        InterviewAdvanceResult result = progressService.advanceAfterAnalysis(
                new InterviewAdvanceCommand(SESSION_ID, ANSWER_ID, null, ANALYZED_AT)
        );

        assertAll(
                () -> assertEquals(true, result.interviewCompleted()),
                () -> assertEquals(InterviewStatus.COMPLETED, session.getStatus()),
                () -> assertEquals(ANALYZED_AT, session.getCompletedAt())
        );
        verify(interviewTurnRepository, never()).save(any());
    }

    @Test
    @DisplayName("꼬리 질문 답변에는 추가 꼬리 질문을 생성할 수 없다")
    void rejectsSecondFollowUpQuestion() {
        InterviewSession session = session();
        InterviewCoreQuestion coreQuestion = coreQuestion(session, 1);
        FollowUpQuestion followUpQuestion = FollowUpQuestion.create(coreQuestion, "첫 번째 꼬리 질문");
        session.advanceTurn();
        Answer answer = transcribedAnswer(
                InterviewTurn.createFollowUp(session, 2, followUpQuestion)
        );
        prepare(session, answer);

        assertThrows(
                IllegalStateException.class,
                () -> progressService.advanceAfterAnalysis(
                        new InterviewAdvanceCommand(
                                SESSION_ID,
                                ANSWER_ID,
                                "두 번째 꼬리 질문",
                                ANALYZED_AT
                        )
                )
        );

        verify(followUpQuestionRepository, never()).save(any());
        verify(interviewTurnRepository, never()).save(any());
    }

    @Test
    @DisplayName("현재 턴이 아닌 답변은 면접 진행에 반영하지 않는다")
    void rejectsAnswerForNonCurrentTurn() {
        InterviewSession session = session();
        InterviewCoreQuestion coreQuestion = coreQuestion(session, 1);
        session.advanceTurn();
        Answer answer = transcribedAnswer(InterviewTurn.createCore(session, 1, coreQuestion));
        prepare(session, answer);

        assertThrows(
                IllegalStateException.class,
                () -> progressService.advanceAfterAnalysis(
                        new InterviewAdvanceCommand(SESSION_ID, ANSWER_ID, null, ANALYZED_AT)
                )
        );

        verify(coreQuestionRepository, never()).findBySessionIdAndQuestionOrder(any(), any(Integer.class));
        verify(interviewTurnRepository, never()).save(any());
    }

    @Test
    @DisplayName("만료된 면접은 답변 분석이 끝나도 다음 턴으로 진행하지 않는다")
    void rejectsProgressForExpiredInterview() {
        InterviewSession session = session();
        when(interviewSessionRepository.findByIdForUpdate(SESSION_ID))
                .thenReturn(Optional.of(session));
        when(retentionPolicy.isExpired(session, ANALYZED_AT)).thenReturn(true);

        assertThrows(
                IllegalStateException.class,
                () -> progressService.advanceAfterAnalysis(
                        new InterviewAdvanceCommand(
                                SESSION_ID,
                                ANSWER_ID,
                                null,
                                ANALYZED_AT
                        )
                )
        );

        verify(answerRepository, never()).findByIdAndSessionIdForUpdate(any(), any());
        verify(interviewTurnRepository, never()).save(any());
    }

    private void prepare(InterviewSession session, Answer answer) {
        when(interviewSessionRepository.findByIdForUpdate(SESSION_ID))
                .thenReturn(Optional.of(session));
        when(answerRepository.findByIdAndSessionIdForUpdate(ANSWER_ID, SESSION_ID))
                .thenReturn(Optional.of(answer));
    }

    private static InterviewSession session() {
        InterviewSession session = InterviewSession.create(1L, ANALYZED_AT.minusMinutes(10));
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        return session;
    }

    private static InterviewCoreQuestion coreQuestion(InterviewSession session, int order) {
        InterviewCoreQuestion question = InterviewCoreQuestion.create(
                session,
                (long) order,
                "핵심 질문 " + order,
                "CATEGORY_" + order,
                order
        );
        ReflectionTestUtils.setField(question, "id", (long) order);
        return question;
    }

    private static Answer transcribedAnswer(InterviewTurn turn) {
        Answer answer = Answer.create(turn, "audio.webm", "audio/webm", 1_000);
        ReflectionTestUtils.setField(answer, "id", ANSWER_ID);
        answer.completeTranscription("답변 내용", ANALYZED_AT.minusSeconds(1));
        assertNotNull(answer.getTranscript());
        return answer;
    }
}
