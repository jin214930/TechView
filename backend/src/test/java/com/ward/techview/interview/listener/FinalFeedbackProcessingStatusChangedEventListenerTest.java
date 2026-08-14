package com.ward.techview.interview.listener;

import com.ward.techview.interview.repository.InterviewSseEmitterRepository;
import com.ward.techview.processing.api.feedback.FinalFeedbackProcessingStatusChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FinalFeedbackProcessingStatusChangedEventListenerTest {

    private static final Long SESSION_ID = 10L;

    @Mock
    private InterviewSseEmitterRepository emitterRepository;

    @InjectMocks
    private FinalFeedbackProcessingStatusChangedEventListener listener;

    @Test
    @DisplayName("피드백 상태 변경 이벤트를 해당 면접 세션의 SSE 연결로 전달한다")
    void sendsFeedbackStatusChangedToInterviewSession() {
        listener.handle(new FinalFeedbackProcessingStatusChangedEvent(SESSION_ID));

        verify(emitterRepository).sendFeedbackStatusChanged(SESSION_ID);
    }

    @Test
    @DisplayName("피드백 상태 변경은 트랜잭션 커밋 후 전달한다")
    void sendsStatusChangedAfterCommit() throws NoSuchMethodException {
        Method handle = FinalFeedbackProcessingStatusChangedEventListener.class.getMethod(
                "handle",
                FinalFeedbackProcessingStatusChangedEvent.class
        );

        TransactionalEventListener annotation =
                handle.getAnnotation(TransactionalEventListener.class);

        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase());
    }
}
