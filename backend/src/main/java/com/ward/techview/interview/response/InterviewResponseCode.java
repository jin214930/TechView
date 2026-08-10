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
    ANSWER_UPLOAD_METADATA_MISMATCH(3005, HttpStatus.CONFLICT, "기존 답변과 음성 정보가 일치하지 않습니다."),
    ANSWER_NOT_FOUND(3006, HttpStatus.NOT_FOUND, "답변을 찾을 수 없습니다."),
    INTERVIEW_NOT_COMPLETED(3007, HttpStatus.CONFLICT, "완료된 면접이 아닙니다."),
    DAILY_INTERVIEW_LIMIT_EXCEEDED(3008, HttpStatus.TOO_MANY_REQUESTS, "오늘은 이미 면접을 시작했습니다. 내일 다시 이용해 주세요.");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;
}
