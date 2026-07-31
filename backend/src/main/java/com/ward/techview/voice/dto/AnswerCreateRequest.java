package com.ward.techview.voice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AnswerCreateRequest(
        @NotBlank
        String audioContentType,
        @Positive
        int audioDurationMs
) {
}
