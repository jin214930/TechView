package com.ward.techview.voice.listener;

import com.ward.techview.processing.api.SttTranscriptionJobQueuedEvent;
import com.ward.techview.voice.service.SttTranscriptionProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SttTranscriptionJobQueuedEventListener {
    private final SttTranscriptionProcessor processor;

    @Async("processingTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SttTranscriptionJobQueuedEvent event) {
        try {
            processor.process(event.processingJobId());
        } catch (RuntimeException e) {
            log.error("이벤트 기반 STT 작업 처리 중 예상하지 못한 오류가 발생했습니다. processingJobId={}",
                    event.processingJobId(),
                    e
            );
        }
    }
}
