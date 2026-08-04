package com.ward.techview.integration.analysis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "custom.analysis.openai")
public record OpenAiAnswerAnalysisProperties(
        @NotBlank String model,
        @NotBlank String promptVersion,
        @Positive int maxOutputTokens,
        @NotNull Duration timeout
) {
    public OpenAiAnswerAnalysisProperties {
        if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
            throw new IllegalArgumentException("답변 분석 요청 제한 시간은 0보다 커야 합니다.");
        }
    }
}
