package com.ward.techview.account.member.response;

import com.ward.techview.common.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberResponseCode implements ResponseCode {
    MEMBER_NOT_FOUND(2002, HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    ;

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;
}
