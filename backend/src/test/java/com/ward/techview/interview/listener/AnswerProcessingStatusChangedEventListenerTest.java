package com.ward.techview.interview.listener;

import com.ward.techview.interview.repository.InterviewSseEmitterRepository;
import com.ward.techview.processing.api.analysis.AnswerProcessingStatusChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AnswerProcessingStatusChangedEventListenerTest {

    private static final Long SESSION_ID = 10L;

    @Mock
    private InterviewSseEmitterRepository emitterRepository;

    @InjectMocks
    private AnswerProcessingStatusChangedEventListener listener;

    @Test
    @DisplayName("답변 처리 상태 변경 이벤트를 해당 면접 세션의 SSE 연결로 전달한다")
    void sendsProgressChangedToInterviewSession() {
        listener.handle(new AnswerProcessingStatusChangedEvent(SESSION_ID));

        verify(emitterRepository).sendProgressChanged(SESSION_ID);
    }
}
