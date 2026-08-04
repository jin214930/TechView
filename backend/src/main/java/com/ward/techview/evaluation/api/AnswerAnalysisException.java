package com.ward.techview.evaluation.api;

import lombok.Getter;

import java.util.Objects;

@Getter
public class AnswerAnalysisException extends RuntimeException {
    private final AnswerAnalysisFailure failure;

    public AnswerAnalysisException(AnswerAnalysisFailure failure, String message, Throwable cause
    ) {
        super(message, cause);
        this.failure = Objects.requireNonNull(failure);
    }
}
