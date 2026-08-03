package com.ward.techview.voice.api;

import lombok.Getter;

import java.util.Objects;

@Getter
public class AudioStorageException extends RuntimeException {
    private final AudioStorageFailure failure;

    public AudioStorageException(AudioStorageFailure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = Objects.requireNonNull(failure, "저장소 오류 유형은 필수입니다.");
    }
}
