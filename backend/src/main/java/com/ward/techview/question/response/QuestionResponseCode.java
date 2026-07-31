package com.ward.techview.question.response;

import com.ward.techview.common.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum QuestionResponseCode implements ResponseCode {
    QUESTION_POOL_INSUFFICIENT(6000, HttpStatus.INTERNAL_SERVER_ERROR, "면접 질문 풀이 충분하지 않습니다."),
    ;

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;
}
