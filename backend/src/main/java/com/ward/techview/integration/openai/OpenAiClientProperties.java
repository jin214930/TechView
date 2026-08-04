package com.ward.techview.integration.openai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties(prefix = "custom.openai")
public record OpenAiClientProperties(
        @NotNull URI baseUrl,
        @NotBlank String apiKey
) {
}
