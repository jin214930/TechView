package com.ward.techview.interview.listener;

import com.ward.techview.interview.repository.InterviewSseEmitterRepository;
import com.ward.techview.processing.api.analysis.AnswerProcessingStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AnswerProcessingStatusChangedEventListener {
    private final InterviewSseEmitterRepository emitterRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AnswerProcessingStatusChangedEvent event) {
        emitterRepository.sendProgressChanged(event.interviewSessionId());
    }
}
