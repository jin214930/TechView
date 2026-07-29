package com.ward.techview.account.auth.response;

import com.ward.techview.common.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthResponseCode implements ResponseCode {
    SIGNUP_SUCCESS(0, HttpStatus.CREATED, "회원가입이 완료되었습니다."),
    EMAIL_CONFLICT(2000, HttpStatus.CONFLICT, "이미 존재하는 이메일입니다.");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;
}
