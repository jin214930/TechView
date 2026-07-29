package com.ward.techview.common.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        @JsonIgnore HttpStatus httpStatus,
        int code,
        String message,
        T data
) {
    public static ApiResponse<Void> from(ResponseCode responseCode) {
        return new ApiResponse<>(responseCode.getHttpStatus(), responseCode.getCode(), responseCode.getMessage(), null);
    }

    public static <T> ApiResponse<T> from(ResponseCode responseCode, T data) {
        return new ApiResponse<>(responseCode.getHttpStatus(), responseCode.getCode(), responseCode.getMessage(), data);
    }
}
