package com.ward.techview.interview.listener;

import com.ward.techview.interview.repository.InterviewSseEmitterRepository;
import com.ward.techview.processing.api.feedback.FinalFeedbackProcessingStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FinalFeedbackProcessingStatusChangedEventListener {
    private final InterviewSseEmitterRepository emitterRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(FinalFeedbackProcessingStatusChangedEvent event) {
        emitterRepository.sendFeedbackStatusChanged(event.sessionId());
    }
}
