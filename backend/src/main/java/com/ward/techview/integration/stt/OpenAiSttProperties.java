package com.ward.techview.integration.stt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "custom.stt.openai")
public record OpenAiSttProperties(
        @NotBlank String model,
        @NotBlank String language,
        @NotNull Duration timeout
) {
    public OpenAiSttProperties {
        if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
            throw new IllegalArgumentException("STT 요청 제한 시간은 0보다 커야 합니다.");
        }
    }
}
