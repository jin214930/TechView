package com.ward.techview.interview.service;

import com.ward.techview.account.api.MemberLockManager;
import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.interview.dto.ActiveInterviewResponse;
import com.ward.techview.interview.dto.InterviewStartResponse;
import com.ward.techview.interview.entity.InterviewCoreQuestion;
import com.ward.techview.interview.entity.InterviewSession;
import com.ward.techview.interview.entity.InterviewStatus;
import com.ward.techview.interview.entity.InterviewTurn;
import com.ward.techview.interview.entity.InterviewTurnType;
import com.ward.techview.interview.repository.InterviewCoreQuestionRepository;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import com.ward.techview.interview.repository.InterviewTurnRepository;
import com.ward.techview.interview.response.InterviewResponseCode;
import com.ward.techview.question.api.QuestionSelector;
import com.ward.techview.question.api.SelectedQuestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final Long TURN_ID = 100L;

    @Mock
    private InterviewSessionRepository interviewSessionRepository;

    @Mock
    private InterviewCoreQuestionRepository interviewCoreQuestionRepository;

    @Mock
    private InterviewTurnRepository interviewTurnRepository;

    @Mock
    private MemberLockManager memberLockManager;

    @Mock
    private QuestionSelector questionSelector;

    @InjectMocks
    private InterviewService interviewService;

    @Test
    @DisplayName("30일 이내 진행 중인 면접 세션을 반환한다")
    void shouldReturnActiveSessionWhenRecentInProgressSessionExists() {
        InterviewSession session = session();
        when(interviewSessionRepository.findActiveSession(
                eq(MEMBER_ID),
                eq(InterviewStatus.IN_PROGRESS),
                any(OffsetDateTime.class)
        )).thenReturn(Optional.of(session));

        ActiveInterviewResponse response = interviewService.getActiveSession(MEMBER_ID);

        assertAll(
                () -> assertTrue(response.hasActiveSession()),
                () -> assertEquals(SESSION_ID, response.sessionId()),
                () -> assertEquals(session.getStartedAt(), response.startedAt()),
                () -> assertEquals(1, response.currentTurnOrder())
        );
        verifyThirtyDayCutoff();
    }

    @Test
    @DisplayName("진행 중인 면접 세션이 없으면 비활성 응답을 반환한다")
    void shouldReturnInactiveResponseWhenActiveSessionDoesNotExist() {
        when(interviewSessionRepository.findActiveSession(
                eq(MEMBER_ID),
                eq(InterviewStatus.IN_PROGRESS),
                any(OffsetDateTime.class)
        )).thenReturn(Optional.empty());

        ActiveInterviewResponse response = interviewService.getActiveSession(MEMBER_ID);

        assertAll(
                () -> assertFalse(response.hasActiveSession()),
                () -> assertNull(response.sessionId()),
                () -> assertNull(response.startedAt()),
                () -> assertNull(response.currentTurnOrder())
        );
    }

    @Test
    @DisplayName("활성 세션이 있으면 회원 락을 획득한 후 현재 턴을 재개한다")
    void shouldResumeCurrentTurnWhenActiveSessionExists() {
        InterviewSession session = session();
        InterviewTurn turn = coreTurn(session, TURN_ID);
        when(interviewSessionRepository.findActiveSession(
                eq(MEMBER_ID),
                eq(InterviewStatus.IN_PROGRESS),
                any(OffsetDateTime.class)
        )).thenReturn(Optional.of(session));
        when(interviewTurnRepository.findCurrentTurn(SESSION_ID, 1)).thenReturn(Optional.of(turn));

        InterviewStartResponse response = interviewService.startOrResume(MEMBER_ID);

        assertAll(
                () -> assertTrue(response.resumed()),
                () -> assertEquals(SESSION_ID, response.sessionId()),
                () -> assertEquals(TURN_ID, response.turnId()),
                () -> assertEquals(1, response.turnOrder()),
                () -> assertEquals(InterviewTurnType.CORE, response.turnType()),
                () -> assertEquals("첫 번째 질문", response.question())
        );
        InOrder order = inOrder(memberLockManager, interviewSessionRepository);
        order.verify(memberLockManager).lock(MEMBER_ID);
        order.verify(interviewSessionRepository).findActiveSession(
                eq(MEMBER_ID),
                eq(InterviewStatus.IN_PROGRESS),
                any(OffsetDateTime.class)
        );
        verify(questionSelector, never()).selectCoreQuestions();
        verify(interviewCoreQuestionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("활성 세션이 없으면 질문 5개와 첫 번째 핵심 턴을 생성한다")
    void shouldCreateSessionCoreQuestionsAndFirstTurnWhenActiveSessionDoesNotExist() {
        List<SelectedQuestion> selectedQuestions = selectedQuestions(5);
        when(interviewSessionRepository.findActiveSession(
                eq(MEMBER_ID),
                eq(InterviewStatus.IN_PROGRESS),
                any(OffsetDateTime.class)
        )).thenReturn(Optional.empty());
        when(questionSelector.selectCoreQuestions()).thenReturn(selectedQuestions);
        when(interviewSessionRepository.save(any(InterviewSession.class))).thenAnswer(invocation -> {
            InterviewSession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", SESSION_ID);
            return session;
        });
        when(interviewCoreQuestionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(interviewTurnRepository.save(any(InterviewTurn.class))).thenAnswer(invocation -> {
            InterviewTurn turn = invocation.getArgument(0);
            ReflectionTestUtils.setField(turn, "id", TURN_ID);
            return turn;
        });

        InterviewStartResponse response = interviewService.startOrResume(MEMBER_ID);

        assertAll(
                () -> assertFalse(response.resumed()),
                () -> assertEquals(SESSION_ID, response.sessionId()),
                () -> assertEquals(TURN_ID, response.turnId()),
                () -> assertEquals(1, response.turnOrder()),
                () -> assertEquals(InterviewTurnType.CORE, response.turnType()),
                () -> assertEquals("질문 1", response.question())
        );
        verify(memberLockManager).lock(MEMBER_ID);
        verify(questionSelector).selectCoreQuestions();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InterviewCoreQuestion>> questionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(interviewCoreQuestionRepository).saveAll(questionsCaptor.capture());
        List<InterviewCoreQuestion> savedQuestions = questionsCaptor.getValue();

        assertAll(
                () -> assertEquals(5, savedQuestions.size()),
                () -> assertEquals(
                        List.of(1, 2, 3, 4, 5),
                        savedQuestions.stream().map(InterviewCoreQuestion::getQuestionOrder).toList()
                ),
                () -> assertEquals(
                        5,
                        savedQuestions.stream().map(InterviewCoreQuestion::getCategory).distinct().count()
                )
        );
    }

    @Test
    @DisplayName("활성 세션의 현재 턴이 없으면 잘못된 면접 상태 예외를 발생시킨다")
    void shouldThrowInvalidStateWhenCurrentTurnDoesNotExist() {
        InterviewSession session = session();
        when(interviewSessionRepository.findActiveSession(
                eq(MEMBER_ID),
                eq(InterviewStatus.IN_PROGRESS),
                any(OffsetDateTime.class)
        )).thenReturn(Optional.of(session));
        when(interviewTurnRepository.findCurrentTurn(SESSION_ID, 1)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> interviewService.startOrResume(MEMBER_ID)
        );

        assertSame(InterviewResponseCode.INTERVIEW_STATE_INVALID, exception.getResponseCode());
        verify(questionSelector, never()).selectCoreQuestions();
    }

    @Test
    @DisplayName("선정된 핵심 질문이 5개가 아니면 면접 세션을 생성하지 않는다")
    void shouldNotCreateSessionWhenSelectedQuestionCountIsInvalid() {
        when(interviewSessionRepository.findActiveSession(
                eq(MEMBER_ID),
                eq(InterviewStatus.IN_PROGRESS),
                any(OffsetDateTime.class)
        )).thenReturn(Optional.empty());
        when(questionSelector.selectCoreQuestions()).thenReturn(selectedQuestions(4));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> interviewService.startOrResume(MEMBER_ID)
        );

        assertSame(InterviewResponseCode.INTERVIEW_STATE_INVALID, exception.getResponseCode());
        verify(interviewSessionRepository, never()).save(any());
        verify(interviewCoreQuestionRepository, never()).saveAll(any());
        verify(interviewTurnRepository, never()).save(any());
    }

    private void verifyThirtyDayCutoff() {
        ArgumentCaptor<OffsetDateTime> cutoffCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(interviewSessionRepository).findActiveSession(
                eq(MEMBER_ID),
                eq(InterviewStatus.IN_PROGRESS),
                cutoffCaptor.capture()
        );

        OffsetDateTime expected = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
        long differenceSeconds = Math.abs(
                expected.toEpochSecond() - cutoffCaptor.getValue().toEpochSecond()
        );
        assertTrue(differenceSeconds <= 1);
    }

    private static InterviewSession session() {
        InterviewSession session = InterviewSession.create(
                MEMBER_ID,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
        );
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        return session;
    }

    private static InterviewTurn coreTurn(InterviewSession session, Long turnId) {
        InterviewCoreQuestion coreQuestion = InterviewCoreQuestion.create(
                session,
                1L,
                "첫 번째 질문",
                "JAVA",
                1
        );
        ReflectionTestUtils.setField(coreQuestion, "id", 20L);

        InterviewTurn turn = InterviewTurn.createCore(session, 1, coreQuestion);
        ReflectionTestUtils.setField(turn, "id", turnId);
        return turn;
    }

    private static List<SelectedQuestion> selectedQuestions(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> new SelectedQuestion(
                        (long) index,
                        "질문 " + index,
                        "CATEGORY_" + index
                ))
                .toList();
    }
}
