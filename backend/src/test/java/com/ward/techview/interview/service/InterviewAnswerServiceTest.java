package com.ward.techview.interview.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.interview.api.AnswerCreateCommand;
import com.ward.techview.interview.api.PreparedAnswer;
import com.ward.techview.interview.entity.Answer;
import com.ward.techview.interview.entity.InterviewCoreQuestion;
import com.ward.techview.interview.entity.InterviewSession;
import com.ward.techview.interview.entity.InterviewStatus;
import com.ward.techview.interview.entity.InterviewTurn;
import com.ward.techview.interview.repository.AnswerRepository;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import com.ward.techview.interview.repository.InterviewTurnRepository;
import com.ward.techview.interview.response.InterviewResponseCode;
import org.junit.jupiter.api.DisplayName;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewAnswerServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final Long TURN_ID = 100L;
    private static final Long ANSWER_ID = 1000L;
    private static final String OBJECT_KEY = "interviews/10/turns/100/audio-key";
    private static final String CONTENT_TYPE = "audio/webm";
    private static final int DURATION_MS = 42_000;

    @Mock
    private InterviewSessionRepository interviewSessionRepository;

    @Mock
    private InterviewTurnRepository interviewTurnRepository;

    @Mock
    private AnswerRepository answerRepository;

    @InjectMocks
    private InterviewAnswerService interviewAnswerService;

    @Test
    @DisplayName("회원의 진행 중인 현재 턴에 답변을 생성한다")
    void shouldCreateAnswerForCurrentTurn() {
        InterviewSession session = session(InterviewStatus.IN_PROGRESS, 1);
        InterviewTurn turn = turn(session, 1);
        when(interviewSessionRepository.findByIdAndMemberIdForUpdate(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));
        when(interviewTurnRepository.findByIdAndSessionId(TURN_ID, SESSION_ID))
                .thenReturn(Optional.of(turn));
        when(answerRepository.findByInterviewTurnId(TURN_ID)).thenReturn(Optional.empty());
        when(answerRepository.save(any(Answer.class))).thenAnswer(invocation -> {
            Answer answer = invocation.getArgument(0);
            ReflectionTestUtils.setField(answer, "id", ANSWER_ID);
            return answer;
        });

        PreparedAnswer result = interviewAnswerService.prepareAnswer(command());

        assertAll(
                () -> assertEquals(ANSWER_ID, result.id()),
                () -> assertEquals(OBJECT_KEY, result.audioObjectKey()),
                () -> assertEquals(CONTENT_TYPE, result.audioContentType()),
                () -> assertEquals(true, result.created())
        );
        ArgumentCaptor<Answer> answerCaptor = ArgumentCaptor.forClass(Answer.class);
        verify(answerRepository).save(answerCaptor.capture());
        Answer savedAnswer = answerCaptor.getValue();
        assertAll(
                () -> assertSame(turn, savedAnswer.getInterviewTurn()),
                () -> assertEquals(OBJECT_KEY, savedAnswer.getAudioObjectKey()),
                () -> assertEquals(CONTENT_TYPE, savedAnswer.getAudioContentType()),
                () -> assertEquals(DURATION_MS, savedAnswer.getAudioDurationMs())
        );
    }

    @Test
    @DisplayName("회원 소유의 면접 세션이 없으면 답변을 생성하지 않는다")
    void shouldRejectWhenOwnedSessionDoesNotExist() {
        when(interviewSessionRepository.findByIdAndMemberIdForUpdate(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> interviewAnswerService.prepareAnswer(command())
        );

        assertSame(InterviewResponseCode.INTERVIEW_NOT_FOUND, exception.getResponseCode());
        verify(interviewTurnRepository, never()).findByIdAndSessionId(any(), any());
        verify(answerRepository, never()).save(any());
    }

    @Test
    @DisplayName("진행 중인 면접이 아니면 답변을 생성하지 않는다")
    void shouldRejectWhenInterviewIsNotInProgress() {
        InterviewSession session = session(InterviewStatus.COMPLETED, 1);
        when(interviewSessionRepository.findByIdAndMemberIdForUpdate(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> interviewAnswerService.prepareAnswer(command())
        );

        assertSame(InterviewResponseCode.INTERVIEW_NOT_IN_PROGRESS, exception.getResponseCode());
        verify(interviewTurnRepository, never()).findByIdAndSessionId(any(), any());
        verify(answerRepository, never()).save(any());
    }

    @Test
    @DisplayName("면접 세션에 해당 턴이 없으면 답변을 생성하지 않는다")
    void shouldRejectWhenTurnDoesNotExistInSession() {
        InterviewSession session = session(InterviewStatus.IN_PROGRESS, 1);
        when(interviewSessionRepository.findByIdAndMemberIdForUpdate(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));
        when(interviewTurnRepository.findByIdAndSessionId(TURN_ID, SESSION_ID))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> interviewAnswerService.prepareAnswer(command())
        );

        assertSame(InterviewResponseCode.INTERVIEW_TURN_NOT_FOUND, exception.getResponseCode());
        verify(answerRepository, never()).save(any());
    }

    @Test
    @DisplayName("현재 턴이 아니면 답변을 생성하지 않는다")
    void shouldRejectWhenTurnIsNotCurrent() {
        InterviewSession session = session(InterviewStatus.IN_PROGRESS, 1);
        InterviewTurn turn = turn(session, 2);
        when(interviewSessionRepository.findByIdAndMemberIdForUpdate(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));
        when(interviewTurnRepository.findByIdAndSessionId(TURN_ID, SESSION_ID))
                .thenReturn(Optional.of(turn));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> interviewAnswerService.prepareAnswer(command())
        );

        assertSame(InterviewResponseCode.INTERVIEW_TURN_NOT_CURRENT, exception.getResponseCode());
        verify(answerRepository, never()).findByInterviewTurnId(any());
        verify(answerRepository, never()).save(any());
    }

    @Test
    @DisplayName("현재 턴에 답변이 이미 있으면 기존 답변을 반환한다")
    void shouldReturnExistingAnswerWhenAnswerAlreadyExists() {
        InterviewSession session = session(InterviewStatus.IN_PROGRESS, 1);
        InterviewTurn turn = turn(session, 1);
        Answer existingAnswer = Answer.create(turn, OBJECT_KEY, CONTENT_TYPE, DURATION_MS);
        ReflectionTestUtils.setField(existingAnswer, "id", ANSWER_ID);
        when(interviewSessionRepository.findByIdAndMemberIdForUpdate(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));
        when(interviewTurnRepository.findByIdAndSessionId(TURN_ID, SESSION_ID))
                .thenReturn(Optional.of(turn));
        when(answerRepository.findByInterviewTurnId(TURN_ID)).thenReturn(Optional.of(existingAnswer));

        PreparedAnswer result = interviewAnswerService.prepareAnswer(command());

        assertAll(
                () -> assertEquals(ANSWER_ID, result.id()),
                () -> assertEquals(OBJECT_KEY, result.audioObjectKey()),
                () -> assertEquals(CONTENT_TYPE, result.audioContentType()),
                () -> assertEquals(false, result.created())
        );

        verify(answerRepository, never()).save(any());
    }

    @Test
    @DisplayName("기존 답변과 음성 재생 시간이 다르면 URL을 재발급하지 않는다")
    void shouldRejectWhenExistingAnswerMetadataDoesNotMatch() {
        InterviewSession session = session(InterviewStatus.IN_PROGRESS, 1);
        InterviewTurn turn = turn(session, 1);
        Answer existingAnswer = Answer.create(turn, OBJECT_KEY, CONTENT_TYPE, DURATION_MS);
        ReflectionTestUtils.setField(existingAnswer, "id", ANSWER_ID);
        when(interviewSessionRepository.findByIdAndMemberIdForUpdate(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));
        when(interviewTurnRepository.findByIdAndSessionId(TURN_ID, SESSION_ID))
                .thenReturn(Optional.of(turn));
        when(answerRepository.findByInterviewTurnId(TURN_ID)).thenReturn(Optional.of(existingAnswer));

        AnswerCreateCommand mismatchedCommand = new AnswerCreateCommand(
                MEMBER_ID,
                SESSION_ID,
                TURN_ID,
                "unused-new-object-key",
                CONTENT_TYPE,
                DURATION_MS + 1
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> interviewAnswerService.prepareAnswer(mismatchedCommand)
        );

        assertSame(InterviewResponseCode.ANSWER_UPLOAD_METADATA_MISMATCH, exception.getResponseCode());
        verify(answerRepository, never()).save(any());
    }

    private static AnswerCreateCommand command() {
        return new AnswerCreateCommand(
                MEMBER_ID,
                SESSION_ID,
                TURN_ID,
                OBJECT_KEY,
                CONTENT_TYPE,
                DURATION_MS
        );
    }

    private static InterviewSession session(InterviewStatus status, int currentTurnOrder) {
        InterviewSession session = InterviewSession.create(
                MEMBER_ID,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        ReflectionTestUtils.setField(session, "status", status);
        ReflectionTestUtils.setField(session, "currentTurnOrder", currentTurnOrder);
        return session;
    }

    private static InterviewTurn turn(InterviewSession session, int turnOrder) {
        InterviewCoreQuestion coreQuestion = InterviewCoreQuestion.create(
                session,
                1L,
                "자바의 다형성을 설명해 주세요.",
                "JAVA",
                1
        );
        ReflectionTestUtils.setField(coreQuestion, "id", 20L);

        InterviewTurn turn = InterviewTurn.createCore(session, turnOrder, coreQuestion);
        ReflectionTestUtils.setField(turn, "id", TURN_ID);
        return turn;
    }
}
