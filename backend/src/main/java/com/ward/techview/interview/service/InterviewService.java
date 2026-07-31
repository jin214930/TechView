package com.ward.techview.interview.service;

import com.ward.techview.interview.dto.ActiveInterviewResponse;
import com.ward.techview.interview.entity.InterviewStatus;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class InterviewService {
    private static final long ACTIVE_SESSION_RETENTION_DAYS = 30;

    private final InterviewSessionRepository interviewSessionRepository;

    @Transactional(readOnly = true)
    public ActiveInterviewResponse getActiveSession(Long memberId) {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(ACTIVE_SESSION_RETENTION_DAYS);

        return interviewSessionRepository.findActiveSession(memberId, InterviewStatus.IN_PROGRESS, cutoff)
                .map(ActiveInterviewResponse::from)
                .orElseGet(ActiveInterviewResponse::none);
    }
}
