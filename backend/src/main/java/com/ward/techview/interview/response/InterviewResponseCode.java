package com.ward.techview.interview.response;

import com.ward.techview.common.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InterviewResponseCode implements ResponseCode {

    INTERVIEW_STATE_INVALID(3000, HttpStatus.INTERNAL_SERVER_ERROR, "면접 진행 상태가 올바르지 않습니다.");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;
}
