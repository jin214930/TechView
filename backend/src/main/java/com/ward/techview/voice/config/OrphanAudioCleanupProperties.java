package com.ward.techview.voice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "custom.voice.orphan-audio-cleanup")
public record OrphanAudioCleanupProperties(
        String prefix,
        Duration gracePeriod,
        String cron,
        String timeZone,
        int pageSize
) {
    public OrphanAudioCleanupProperties {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("고아 음성 객체 접두사는 필수입니다.");
        }

        if (gracePeriod == null || gracePeriod.isZero() || gracePeriod.isNegative()) {
            throw new IllegalArgumentException("고아 음성 보존 시간은 0보다 커야 합니다.");
        }

        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("고아 음성 정리 cron은 필수입니다.");
        }

        if (timeZone == null || timeZone.isBlank()) {
            throw new IllegalArgumentException("고아 음성 정리 시간대는 필수입니다.");
        }

        ZoneId.of(timeZone);

        if (pageSize <= 0 || pageSize > 1_000) {
            throw new IllegalArgumentException(
                    "고아 음성 조회 크기는 1 이상 1000 이하여야 합니다."
            );
        }
    }
}
