package com.ward.techview.integration.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "storage.audio")
public record AudioStorageProperties(
        @NotBlank String bucket,
        @NotBlank String region,
        URI endpoint,
        String accessKey,
        String secretKey,
        @NotNull Duration presignedUrlExpiration,
        boolean pathStyleAccessEnabled
) {
    public AudioStorageProperties {
        if (presignedUrlExpiration != null && (presignedUrlExpiration.isZero() || presignedUrlExpiration.isNegative())) {
            throw new IllegalArgumentException("Presigned URL 만료 시간은 0보다 커야 합니다.");
        }
    }
}