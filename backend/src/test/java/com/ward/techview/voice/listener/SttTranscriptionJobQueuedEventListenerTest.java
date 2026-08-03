package com.ward.techview.voice.listener;

import com.ward.techview.processing.api.SttTranscriptionJobQueuedEvent;
import com.ward.techview.voice.service.SttTranscriptionProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SttTranscriptionJobQueuedEventListenerTest {

    private static final Long PROCESSING_JOB_ID = 100L;

    @Mock
    private SttTranscriptionProcessor processor;

    @InjectMocks
    private SttTranscriptionJobQueuedEventListener listener;

    @Test
    @DisplayName("대기 이벤트의 STT 작업을 처리기에 전달한다")
    void delegatesQueuedJobToProcessor() {
        listener.handle(new SttTranscriptionJobQueuedEvent(PROCESSING_JOB_ID));

        verify(processor).process(PROCESSING_JOB_ID);
    }

    @Test
    @DisplayName("이벤트 처리 중 예상하지 못한 오류는 리스너 밖으로 전파하지 않는다")
    void catchesUnexpectedProcessorFailure() {
        doThrow(new IllegalStateException("예상하지 못한 오류"))
                .when(processor)
                .process(PROCESSING_JOB_ID);

        assertDoesNotThrow(
                () -> listener.handle(
                        new SttTranscriptionJobQueuedEvent(PROCESSING_JOB_ID)
                )
        );
    }

    @Test
    @DisplayName("작업 이벤트는 트랜잭션 커밋 후 공통 Executor에서 처리한다")
    void usesProcessingExecutorAfterCommit() throws NoSuchMethodException {
        Method handle = SttTranscriptionJobQueuedEventListener.class.getMethod(
                "handle",
                SttTranscriptionJobQueuedEvent.class
        );

        Async async = handle.getAnnotation(Async.class);
        TransactionalEventListener eventListener =
                handle.getAnnotation(TransactionalEventListener.class);

        assertAll(
                () -> assertEquals("processingTaskExecutor", async.value()),
                () -> assertEquals(
                        TransactionPhase.AFTER_COMMIT,
                        eventListener.phase()
                )
        );
    }
}
