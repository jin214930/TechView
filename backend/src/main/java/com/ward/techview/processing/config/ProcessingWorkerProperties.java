package com.ward.techview.processing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "custom.processing.worker")
public record ProcessingWorkerProperties(
        Duration leaseDuration,
        int concurrency,
        int queueCapacity,
        Duration pollingDelay,
        int pollingBatchSize
) {
    public ProcessingWorkerProperties {
        Objects.requireNonNull(leaseDuration, "lease 기간은 필수입니다.");

        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("lease 기간은 0보다 커야 합니다.");
        }

        if (concurrency <= 0) {
            throw new IllegalArgumentException("워커 동시 실행 수는 0보다 커야 합니다.");
        }

        if (queueCapacity < 0) {
            throw new IllegalArgumentException("워커 대기열 크기는 0 이상이어야 합니다.");
        }

        Objects.requireNonNull(pollingDelay, "polling 주기는 필수입니다.");

        if (pollingDelay.isZero() || pollingDelay.isNegative()) {
            throw new IllegalArgumentException("polling 주기는 0보다 커야 합니다.");
        }

        if (pollingBatchSize <= 0) {
            throw new IllegalArgumentException("polling 조회 개수는 0보다 커야 합니다.");
        }
    }
}
