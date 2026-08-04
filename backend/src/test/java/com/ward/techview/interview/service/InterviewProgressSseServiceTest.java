package com.ward.techview.interview.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.interview.entity.InterviewSession;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import com.ward.techview.interview.repository.InterviewSseEmitterRepository;
import com.ward.techview.interview.response.InterviewResponseCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class InterviewProgressSseServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;

    @Mock
    private InterviewSessionRepository interviewSessionRepository;

    @Mock
    private InterviewSseEmitterRepository emitterRepository;

    @InjectMocks
    private InterviewProgressSseService service;

    @Test
    @DisplayName("회원 소유의 면접 세션에 SSE 연결을 등록한다")
    void subscribesToOwnedInterviewSession() {
        InterviewSession session = InterviewSession.create(
                MEMBER_ID,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        when(interviewSessionRepository.findByIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));

        SseEmitter result = service.subscribe(MEMBER_ID, SESSION_ID);

        ArgumentCaptor<SseEmitter> emitterCaptor = ArgumentCaptor.forClass(SseEmitter.class);
        verify(emitterRepository).add(eq(SESSION_ID), emitterCaptor.capture());
        assertNotNull(result);
        assertSame(result, emitterCaptor.getValue());
        assertEquals(5 * 60 * 1000L, result.getTimeout());
        result.complete();
    }

    @Test
    @DisplayName("회원 소유가 아닌 면접 세션에는 SSE 연결을 등록하지 않는다")
    void rejectsMissingOrForeignInterviewSession() {
        when(interviewSessionRepository.findByIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.subscribe(MEMBER_ID, SESSION_ID)
        );

        assertSame(InterviewResponseCode.INTERVIEW_NOT_FOUND, exception.getResponseCode());
        verify(emitterRepository, never()).add(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(SseEmitter.class)
        );
    }
}
