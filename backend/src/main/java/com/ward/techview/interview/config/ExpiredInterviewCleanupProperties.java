package com.ward.techview.interview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;

@ConfigurationProperties(
        prefix = "custom.interview.expired-session-cleanup"
)
public record ExpiredInterviewCleanupProperties(
        Duration retentionPeriod,
        String cron,
        String timeZone,
        int batchSize
) {
    public ExpiredInterviewCleanupProperties {
        if (retentionPeriod == null || retentionPeriod.isZero() || retentionPeriod.isNegative()) {
            throw new IllegalArgumentException("면접 세션 보관 기간은 0보다 커야 합니다.");
        }

        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("만료 면접 정리 cron은 필수입니다.");
        }

        if (timeZone == null || timeZone.isBlank()) {
            throw new IllegalArgumentException("만료 면접 정리 시간대는 필수입니다.");
        }

        ZoneId.of(timeZone);

        if (batchSize <= 0) {
            throw new IllegalArgumentException("만료 면접 정리 배치 크기는 0보다 커야 합니다.");
        }
    }
}
