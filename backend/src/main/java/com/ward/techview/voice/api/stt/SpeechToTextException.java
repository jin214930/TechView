package com.ward.techview.voice.api.stt;

import lombok.Getter;

import java.util.Objects;

@Getter
public class SpeechToTextException extends RuntimeException {
    private final SpeechToTextFailure failure;

    public SpeechToTextException(SpeechToTextFailure failure, String message) {
        super(message);
        this.failure = Objects.requireNonNull(failure);
    }

    public SpeechToTextException(SpeechToTextFailure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = Objects.requireNonNull(failure);
    }
}
