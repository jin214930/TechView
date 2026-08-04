package com.ward.techview.interview.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class InterviewSseEmitterRepositoryTest {

    private static final Long SESSION_ID = 10L;

    private final InterviewSseEmitterRepository repository =
            new InterviewSseEmitterRepository();

    @Test
    @DisplayName("동일 면접 세션에 등록된 모든 SSE 연결에 상태 변경을 전송한다")
    void sendsProgressChangedToEveryEmitterInSession() throws IOException {
        SseEmitter firstEmitter = mock(SseEmitter.class);
        SseEmitter secondEmitter = mock(SseEmitter.class);
        SseEmitter otherSessionEmitter = mock(SseEmitter.class);
        repository.add(SESSION_ID, firstEmitter);
        repository.add(SESSION_ID, secondEmitter);
        repository.add(20L, otherSessionEmitter);

        repository.sendProgressChanged(SESSION_ID);

        verify(firstEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(secondEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(otherSessionEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("동일 면접 세션의 모든 SSE 연결에 피드백 상태 변경을 전송한다")
    void sendsFeedbackStatusChangedToEveryEmitterInSession() throws IOException {
        SseEmitter firstEmitter = mock(SseEmitter.class);
        SseEmitter secondEmitter = mock(SseEmitter.class);
        SseEmitter otherSessionEmitter = mock(SseEmitter.class);
        repository.add(SESSION_ID, firstEmitter);
        repository.add(SESSION_ID, secondEmitter);
        repository.add(20L, otherSessionEmitter);

        repository.sendFeedbackStatusChanged(SESSION_ID);

        verify(firstEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(secondEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(otherSessionEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("전송에 실패한 SSE 연결만 제거하고 나머지 연결에는 계속 전송한다")
    void removesOnlyFailedEmitter() throws IOException {
        SseEmitter failedEmitter = mock(SseEmitter.class);
        SseEmitter activeEmitter = mock(SseEmitter.class);
        doThrow(new IOException("연결 종료"))
                .when(failedEmitter)
                .send(any(SseEmitter.SseEventBuilder.class));
        repository.add(SESSION_ID, failedEmitter);
        repository.add(SESSION_ID, activeEmitter);

        repository.sendProgressChanged(SESSION_ID);
        repository.sendProgressChanged(SESSION_ID);

        verify(failedEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(activeEmitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }
}
