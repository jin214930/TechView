package com.ward.techview.interview.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.interview.entity.InterviewSession;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import com.ward.techview.interview.response.InterviewResponseCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewFeedbackAccessServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;

    @Mock
    private InterviewSessionRepository interviewSessionRepository;

    @InjectMocks
    private InterviewFeedbackAccessService service;

    @Test
    @DisplayName("회원이 소유한 완료 면접의 피드백 접근을 허용한다")
    void allowsAccessToOwnedCompletedInterview() {
        InterviewSession session = completedSession();
        when(interviewSessionRepository.findByIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));

        assertDoesNotThrow(
                () -> service.validateCompletedInterview(MEMBER_ID, SESSION_ID)
        );

        verify(interviewSessionRepository).findByIdAndMemberId(SESSION_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("존재하지 않거나 다른 회원의 면접 접근을 거부한다")
    void rejectsMissingOrForeignInterview() {
        when(interviewSessionRepository.findByIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.validateCompletedInterview(MEMBER_ID, SESSION_ID)
        );

        assertSame(InterviewResponseCode.INTERVIEW_NOT_FOUND, exception.getResponseCode());
    }

    @Test
    @DisplayName("진행 중인 면접의 피드백 접근을 거부한다")
    void rejectsInterviewThatIsNotCompleted() {
        InterviewSession session = InterviewSession.create(MEMBER_ID, now());
        when(interviewSessionRepository.findByIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.validateCompletedInterview(MEMBER_ID, SESSION_ID)
        );

        assertSame(InterviewResponseCode.INTERVIEW_NOT_COMPLETED, exception.getResponseCode());
    }

    @Test
    @DisplayName("회원 ID 또는 면접 세션 ID가 없으면 저장소를 조회하지 않는다")
    void rejectsNullIdentifiers() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> service.validateCompletedInterview(null, SESSION_ID)
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> service.validateCompletedInterview(MEMBER_ID, null)
                )
        );

        verifyNoInteractions(interviewSessionRepository);
    }

    private static InterviewSession completedSession() {
        InterviewSession session = InterviewSession.create(MEMBER_ID, now());
        session.complete(now().plusMinutes(15));
        return session;
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
