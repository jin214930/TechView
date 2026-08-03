package com.ward.techview.voice.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "custom.voice.upload")
public record VoiceUploadProperties(
        @NotNull DataSize maxFileSize
) {
    public VoiceUploadProperties {
        if (maxFileSize != null && maxFileSize.toBytes() <= 0) {
            throw new IllegalArgumentException("최대 음성 파일 크기는 0보다 커야 합니다.");
        }
    }
}
