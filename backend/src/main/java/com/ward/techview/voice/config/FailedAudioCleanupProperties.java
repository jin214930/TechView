package com.ward.techview.voice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "custom.voice.failed-audio-cleanup")
public record FailedAudioCleanupProperties(
        String cron,
        String timeZone,
        int batchSize
) {
    public FailedAudioCleanupProperties {
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("실패 음성 정리 cron은 필수입니다.");
        }

        if (timeZone == null || timeZone.isBlank()) {
            throw new IllegalArgumentException("실패 음성 정리 시간대는 필수입니다.");
        }

        if (batchSize <= 0) {
            throw new IllegalArgumentException("실패 음성 정리 배치 크기는 0보다 커야 합니다.");
        }
    }
}
