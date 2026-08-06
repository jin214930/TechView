package com.ward.techview.voice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AnswerCreateRequest(
        @NotBlank
        String audioContentType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @Positive
        int audioDurationMs
) {
}
