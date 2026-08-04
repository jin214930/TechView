package com.ward.techview.integration.feedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "custom.feedback.openai")
public record OpenAiInterviewFeedbackProperties(
        @NotBlank String model,
        @NotBlank String promptVersion,
        @Positive int maxOutputTokens,
        @NotNull Duration timeout
) {
    public OpenAiInterviewFeedbackProperties {
        if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
            throw new IllegalArgumentException("종합 피드백 요청 제한 시간은 0보다 커야 합니다.");
        }
    }
}
