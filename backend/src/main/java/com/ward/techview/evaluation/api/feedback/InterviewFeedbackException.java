package com.ward.techview.evaluation.api.feedback;

import lombok.Getter;

import java.util.Objects;

@Getter
public class InterviewFeedbackException extends RuntimeException {
    private final InterviewFeedbackFailure failure;

    public InterviewFeedbackException(InterviewFeedbackFailure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = Objects.requireNonNull(failure, "실패 유형은 필수입니다.");
    }
}
