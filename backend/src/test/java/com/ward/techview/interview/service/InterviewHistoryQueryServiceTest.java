package com.ward.techview.interview.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.common.response.CommonResponseCode;
import com.ward.techview.interview.api.CompletedInterviewSummary;
import com.ward.techview.interview.entity.InterviewSession;
import com.ward.techview.interview.entity.InterviewStatus;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewHistoryQueryServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;

    @Mock
    private InterviewSessionRepository interviewSessionRepository;

    @InjectMocks
    private InterviewHistoryQueryService service;

    @Test
    @DisplayName("완료된 면접을 ID 내림차순으로 조회하고 요약 정보로 변환한다")
    void getsCompletedInterviewsOrderedByIdDescending() {
        InterviewSession session = completedSession();
        PageRequest pageRequest = PageRequest.of(
                1,
                10,
                Sort.by(Sort.Direction.DESC, "id")
        );
        Page<InterviewSession> sessions = new PageImpl<>(
                List.of(session),
                pageRequest,
                11
        );

        when(interviewSessionRepository.findAllByMemberIdAndStatus(
                MEMBER_ID,
                InterviewStatus.COMPLETED,
                pageRequest
        )).thenReturn(sessions);

        Page<CompletedInterviewSummary> result = service.getCompletedInterviews(MEMBER_ID, 1, 10);

        CompletedInterviewSummary summary = result.getContent().getFirst();
        assertAll(
                () -> assertEquals(SESSION_ID, summary.sessionId()),
                () -> assertEquals(session.getStartedAt(), summary.startedAt()),
                () -> assertEquals(session.getCompletedAt(), summary.completedAt()),
                () -> assertEquals(1, result.getNumber()),
                () -> assertEquals(10, result.getSize()),
                () -> assertEquals(11, result.getTotalElements())
        );

        verify(interviewSessionRepository).findAllByMemberIdAndStatus(
                MEMBER_ID,
                InterviewStatus.COMPLETED,
                pageRequest
        );
    }

    @Test
    @DisplayName("회원 ID가 없으면 완료된 면접을 조회하지 않는다")
    void rejectsNullMemberId() {
        assertThrows(
                NullPointerException.class,
                () -> service.getCompletedInterviews(null, 0, 10)
        );

        verifyNoInteractions(interviewSessionRepository);
    }

    @Test
    @DisplayName("페이지 번호와 크기가 허용 범위를 벗어나면 요청을 거부한다")
    void rejectsInvalidPageRequest() {
        BusinessException negativePage = assertThrows(
                BusinessException.class,
                () -> service.getCompletedInterviews(MEMBER_ID, -1, 10)
        );
        BusinessException zeroSize = assertThrows(
                BusinessException.class,
                () -> service.getCompletedInterviews(MEMBER_ID, 0, 0)
        );
        BusinessException oversized = assertThrows(
                BusinessException.class,
                () -> service.getCompletedInterviews(MEMBER_ID, 0, 101)
        );

        assertAll(
                () -> assertSame(CommonResponseCode.INVALID_REQUEST, negativePage.getResponseCode()),
                () -> assertSame(CommonResponseCode.INVALID_REQUEST, zeroSize.getResponseCode()),
                () -> assertSame(CommonResponseCode.INVALID_REQUEST, oversized.getResponseCode())
        );

        verifyNoInteractions(interviewSessionRepository);
    }

    private static InterviewSession completedSession() {
        OffsetDateTime startedAt = OffsetDateTime.of(
                2026,
                8,
                4,
                10,
                0,
                0,
                0,
                ZoneOffset.UTC
        );
        InterviewSession session = InterviewSession.create(MEMBER_ID, startedAt);
        session.complete(startedAt.plusMinutes(15));
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        return session;
    }
}
