package com.ward.techview.interview.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.interview.api.answer.AnswerCreateCommand;
import com.ward.techview.interview.api.answer.AnswerInfo;
import com.ward.techview.interview.api.answer.AnswerTranscriptionCommand;
import com.ward.techview.interview.api.answer.PreparedAnswer;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
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

    @Mock
    private InterviewRetentionPolicy retentionPolicy;

    @InjectMocks
    private InterviewAnswerService interviewAnswerService;

    @BeforeEach
    void setUpRetentionPolicy() {
        lenient().when(retentionPolicy.isExpired(
                any(InterviewSession.class),
                any(OffsetDateTime.class)
        )).thenReturn(false);
    }

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
    @DisplayName("만료된 진행 중 면접에는 답변을 생성하지 않는다")
    void shouldRejectAnswerCreationForExpiredInterview() {
        InterviewSession session = session(InterviewStatus.IN_PROGRESS, 1);
        when(interviewSessionRepository.findByIdAndMemberIdForUpdate(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));
        when(retentionPolicy.isExpired(eq(session), any(OffsetDateTime.class)))
                .thenReturn(true);

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

    @Test
    @DisplayName("회원 소유의 답변 정보를 조회한다")
    void shouldGetOwnedAnswerInfo() {
        InterviewSession session = session(InterviewStatus.IN_PROGRESS, 1);
        InterviewTurn turn = turn(session, 1);
        Answer answer = Answer.create(turn, OBJECT_KEY, CONTENT_TYPE, DURATION_MS);
        ReflectionTestUtils.setField(answer, "id", ANSWER_ID);
        when(answerRepository.findByIdAndMemberId(ANSWER_ID, MEMBER_ID))
                .thenReturn(Optional.of(answer));

        AnswerInfo result = interviewAnswerService.getAnswer(MEMBER_ID, ANSWER_ID);

        assertAll(
                () -> assertEquals(ANSWER_ID, result.answerId()),
                () -> assertEquals(SESSION_ID, result.interviewSessionId()),
                () -> assertEquals(OBJECT_KEY, result.audioObjectKey()),
                () -> assertEquals(CONTENT_TYPE, result.audioContentType())
        );
    }

    @Test
    @DisplayName("완료된 면접에서도 회원 소유의 답변 정보를 조회한다")
    void shouldGetOwnedAnswerInfoFromCompletedInterview() {
        InterviewSession session = session(InterviewStatus.COMPLETED, 1);
        InterviewTurn turn = turn(session, 1);
        Answer answer = Answer.create(turn, OBJECT_KEY, CONTENT_TYPE, DURATION_MS);
        ReflectionTestUtils.setField(answer, "id", ANSWER_ID);
        when(answerRepository.findByIdAndMemberId(ANSWER_ID, MEMBER_ID))
                .thenReturn(Optional.of(answer));

        AnswerInfo result = interviewAnswerService.getAnswer(MEMBER_ID, ANSWER_ID);

        assertAll(
                () -> assertEquals(ANSWER_ID, result.answerId()),
                () -> assertEquals(SESSION_ID, result.interviewSessionId()),
                () -> assertEquals(OBJECT_KEY, result.audioObjectKey()),
                () -> assertEquals(CONTENT_TYPE, result.audioContentType())
        );
        verify(retentionPolicy, never()).isExpired(any(), any());
    }

    @Test
    @DisplayName("회원 소유의 답변이 없으면 조회를 거부한다")
    void shouldRejectWhenOwnedAnswerDoesNotExist() {
        when(answerRepository.findByIdAndMemberId(ANSWER_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> interviewAnswerService.getAnswer(MEMBER_ID, ANSWER_ID)
        );

        assertSame(InterviewResponseCode.ANSWER_NOT_FOUND, exception.getResponseCode());
    }

    @Test
    @DisplayName("면접 세션을 잠근 후 현재 턴의 답변 정보를 반환한다")
    void shouldLockSessionAndReturnAnswerForProcessing() {
        InterviewSession session = session(InterviewStatus.IN_PROGRESS, 1);
        InterviewTurn turn = turn(session, 1);
        Answer answer = Answer.create(turn, OBJECT_KEY, CONTENT_TYPE, DURATION_MS);
        ReflectionTestUtils.setField(answer, "id", ANSWER_ID);
        when(interviewSessionRepository.findByIdAndMemberIdForUpdate(
                SESSION_ID,
                MEMBER_ID
        )).thenReturn(Optional.of(session));
        when(answerRepository.findByIdAndSessionId(ANSWER_ID, SESSION_ID))
                .thenReturn(Optional.of(answer));

        AnswerInfo result = interviewAnswerService.lockAnswerForProcessing(
                MEMBER_ID,
                SESSION_ID,
                ANSWER_ID
        );

        assertAll(
                () -> assertEquals(ANSWER_ID, result.answerId()),
                () -> assertEquals(SESSION_ID, result.interviewSessionId()),
                () -> assertEquals(OBJECT_KEY, result.audioObjectKey()),
                () -> assertEquals(CONTENT_TYPE, result.audioContentType())
        );
    }

    @Test
    @DisplayName("회원 소유의 면접 세션이 없으면 처리할 답변을 조회하지 않는다")
    void shouldRejectProcessingWhenOwnedSessionDoesNotExist() {
        when(interviewSessionRepository.findByIdAndMemberIdForUpdate(
                SESSION_ID,
                MEMBER_ID
        )).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> interviewAnswerService.lockAnswerForProcessing(
                        MEMBER_ID,
                        SESSION_ID,
                        ANSWER_ID
                )
        );

        assertSame(InterviewResponseCode.INTERVIEW_NOT_FOUND, exception.getResponseCode());
        verify(answerRepository, never()).findByIdAndSessionId(any(), any());
    }

    @Test
    @DisplayName("답변을 잠그고 STT 전사 결과를 저장한다")
    void shouldCompleteAnswerTranscription() {
        InterviewSession session = session(InterviewStatus.IN_PROGRESS, 1);
        InterviewTurn turn = turn(session, 1);
        Answer answer = Answer.create(turn, OBJECT_KEY, CONTENT_TYPE, DURATION_MS);
        OffsetDateTime transcribedAt = OffsetDateTime.now(ZoneOffset.UTC);
        when(answerRepository.findByIdAndSessionIdForUpdate(ANSWER_ID, SESSION_ID))
                .thenReturn(Optional.of(answer));

        interviewAnswerService.completeTranscription(
                new AnswerTranscriptionCommand(
                        SESSION_ID,
                        ANSWER_ID,
                        "스프링 빈의 의존 관계는 컨테이너가 주입합니다.",
                        transcribedAt
                )
        );

        assertAll(
                () -> assertEquals(
                        "스프링 빈의 의존 관계는 컨테이너가 주입합니다.",
                        answer.getTranscript()
                ),
                () -> assertEquals(transcribedAt, answer.getTranscribedAt())
        );
        verify(answerRepository).findByIdAndSessionIdForUpdate(ANSWER_ID, SESSION_ID);
    }

    @Test
    @DisplayName("만료된 면접의 답변은 STT 작업 등록을 위해 잠글 수 없다")
    void shouldRejectProcessingLockForExpiredInterview() {
        InterviewSession session = session(InterviewStatus.IN_PROGRESS, 1);
        when(interviewSessionRepository.findByIdAndMemberIdForUpdate(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));
        when(retentionPolicy.isExpired(eq(session), any(OffsetDateTime.class)))
                .thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> interviewAnswerService.lockAnswerForProcessing(
                        MEMBER_ID,
                        SESSION_ID,
                        ANSWER_ID
                )
        );

        assertSame(InterviewResponseCode.INTERVIEW_NOT_IN_PROGRESS, exception.getResponseCode());
        verify(answerRepository, never()).findByIdAndSessionId(any(), any());
    }

    @Test
    @DisplayName("작업의 면접 세션에 속한 답변이 없으면 전사 결과를 저장하지 않는다")
    void shouldRejectTranscriptionWhenAnswerDoesNotExistInSession() {
        when(answerRepository.findByIdAndSessionIdForUpdate(ANSWER_ID, SESSION_ID))
                .thenReturn(Optional.empty());
        AnswerTranscriptionCommand command = new AnswerTranscriptionCommand(
                SESSION_ID,
                ANSWER_ID,
                "전사 결과",
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> interviewAnswerService.completeTranscription(command)
        );

        assertSame(InterviewResponseCode.ANSWER_NOT_FOUND, exception.getResponseCode());
        verify(answerRepository).findByIdAndSessionIdForUpdate(ANSWER_ID, SESSION_ID);
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
