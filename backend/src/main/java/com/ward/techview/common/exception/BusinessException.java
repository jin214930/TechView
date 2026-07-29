package com.ward.techview.common.exception;

import com.ward.techview.common.response.ResponseCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ResponseCode responseCode;

    public BusinessException(ResponseCode responseCode) {
        super(responseCode.getMessage());
        this.responseCode = responseCode;
    }
}
