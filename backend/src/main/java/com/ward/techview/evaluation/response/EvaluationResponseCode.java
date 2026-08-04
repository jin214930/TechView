package com.ward.techview.evaluation.response;

import com.ward.techview.common.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum EvaluationResponseCode implements ResponseCode {
    INTERVIEW_FEEDBACK_STATE_INVALID(5000, HttpStatus.INTERNAL_SERVER_ERROR, "피드백 처리 상태가 올바르지 않습니다.");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;
}
