package com.ward.techview.interview.service;

import com.ward.techview.interview.config.ExpiredInterviewCleanupProperties;
import com.ward.techview.interview.entity.InterviewSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class InterviewRetentionPolicy {
    private final ExpiredInterviewCleanupProperties properties;

    public OffsetDateTime calculateCutoff(OffsetDateTime now) {
        Objects.requireNonNull(now, "현재 시각은 필수입니다.");

        return now.minus(properties.retentionPeriod());
    }

    public boolean isExpired(InterviewSession session, OffsetDateTime now) {
        Objects.requireNonNull(session, "면접 세션은 필수입니다.");

        return session.isExpired(calculateCutoff(now));
    }
}
