package com.ward.techview.interview.response;

import com.ward.techview.common.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InterviewResponseCode implements ResponseCode {

    INTERVIEW_STATE_INVALID(3000, HttpStatus.INTERNAL_SERVER_ERROR, "면접 진행 상태가 올바르지 않습니다."),
    INTERVIEW_NOT_FOUND(3001, HttpStatus.NOT_FOUND, "면접 세션을 찾을 수 없습니다."),
    INTERVIEW_TURN_NOT_FOUND(3002, HttpStatus.NOT_FOUND, "면접 턴을 찾을 수 없습니다."),
    INTERVIEW_NOT_IN_PROGRESS(3003, HttpStatus.CONFLICT, "진행 중인 면접이 아닙니다."),
    INTERVIEW_TURN_NOT_CURRENT(3004, HttpStatus.CONFLICT, "현재 답변할 수 있는 면접 턴이 아닙니다."),
    ANSWER_ALREADY_EXISTS(3005, HttpStatus.CONFLICT, "이미 답변이 생성된 면접 턴입니다.");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;
}
