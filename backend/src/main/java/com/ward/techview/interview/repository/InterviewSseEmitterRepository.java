package com.ward.techview.interview.repository;

import com.ward.techview.interview.dto.InterviewFeedbackStatusChangedPayload;
import com.ward.techview.interview.dto.InterviewProgressChangedPayload;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InterviewSseEmitterRepository {
    private static final String PROGRESS_CHANGED_EVENT = "progress-changed";

    private static final String FEEDBACK_STATUS_CHANGED_EVENT = "feedback-status-changed";

    private final Map<Long, Map<String, SseEmitter>> emitters = new ConcurrentHashMap<>();

    public String add(Long sessionId, SseEmitter emitter) {
        String emitterId = UUID.randomUUID().toString();

        emitters.compute(sessionId, (ignored, sessionEmitters) -> {
            Map<String, SseEmitter> target = sessionEmitters != null ? sessionEmitters : new ConcurrentHashMap<>();
            target.put(emitterId, emitter);
            return target;
        });

        emitter.onCompletion(() -> remove(sessionId, emitterId));
        emitter.onTimeout(() -> remove(sessionId, emitterId));
        emitter.onError(error -> remove(sessionId, emitterId));

        return emitterId;
    }

    public void sendProgressChanged(Long sessionId) {
        send(sessionId, PROGRESS_CHANGED_EVENT, new InterviewProgressChangedPayload(sessionId));
    }

    public void sendFeedbackStatusChanged(Long sessionId) {
        send(sessionId, FEEDBACK_STATUS_CHANGED_EVENT, new InterviewFeedbackStatusChangedPayload(sessionId));
    }


    private void send(Long sessionId, String eventName, Object data) {
        Map<String, SseEmitter> sessionEmitters = emitters.get(sessionId);

        if (sessionEmitters == null) {
            return;
        }

        sessionEmitters.forEach((emitterId, emitter) -> {
            try {
                emitter.send(
                        SseEmitter.event()
                                .name(eventName)
                                .data(data)
                );
            } catch (IOException | IllegalStateException exception) {
                remove(sessionId, emitterId);
            }
        });
    }

    private void remove(Long sessionId, String emitterId) {
        emitters.computeIfPresent(sessionId, (ignored, sessionEmitters) -> {
            sessionEmitters.remove(emitterId);
            return sessionEmitters.isEmpty() ? null : sessionEmitters;
        });
    }
}
