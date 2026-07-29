package com.ward.techview.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonResponseCode implements ResponseCode {
    SUCCESS(0, HttpStatus.OK, "요청에 성공했습니다."),
    CREATED(0, HttpStatus.CREATED, "리소스가 생성되었습니다."),
    INVALID_REQUEST(1000, HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    VALIDATION_FAILED(1001, HttpStatus.BAD_REQUEST, "요청 값 검증에 실패했습니다."),
    AUTHENTICATION_REQUIRED(1002, HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(1003, HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(1004, HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(1005, HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(1006, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 미디어 타입입니다."),
    INTERNAL_SERVER_ERROR(9000, HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;
}
