package com.ward.techview.processing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "custom.processing.worker")
public record ProcessingWorkerProperties(
        Duration leaseDuration
) {
    public ProcessingWorkerProperties {
        Objects.requireNonNull(leaseDuration, "lease 기간은 필수입니다.");

        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "lease 기간은 0보다 커야 합니다."
            );
        }
    }
}
