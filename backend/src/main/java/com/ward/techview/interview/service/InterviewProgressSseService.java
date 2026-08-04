package com.ward.techview.interview.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.interview.dto.InterviewProgressChangedPayload;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import com.ward.techview.interview.repository.InterviewSseEmitterRepository;
import com.ward.techview.interview.response.InterviewResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class InterviewProgressSseService {

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewSseEmitterRepository emitterRepository;

    @Transactional(readOnly = true)
    public SseEmitter subscribe(Long memberId, Long sessionId) {
        interviewSessionRepository.findByIdAndMemberId(sessionId, memberId)
                .orElseThrow(() -> new BusinessException(InterviewResponseCode.INTERVIEW_NOT_FOUND));

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitterRepository.add(sessionId, emitter);

        sendConnectedEvent(emitter, sessionId);

        return emitter;
    }

    private void sendConnectedEvent(SseEmitter emitter, Long sessionId) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .name("connected")
                            .data(new InterviewProgressChangedPayload(sessionId))
            );
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }
}
