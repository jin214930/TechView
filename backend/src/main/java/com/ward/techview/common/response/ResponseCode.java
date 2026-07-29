package com.ward.techview.common.response;

import org.springframework.http.HttpStatus;

public interface ResponseCode {
    int getCode();

    HttpStatus getHttpStatus();

    String getMessage();
}