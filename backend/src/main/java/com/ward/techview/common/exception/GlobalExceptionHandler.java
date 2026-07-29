package com.ward.techview.common.exception;

import com.ward.techview.common.response.ApiResponse;
import com.ward.techview.common.response.CommonResponseCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handle(BusinessException e) {
        return ApiResponse.from(e.getResponseCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handle(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        String errorMessage = bindingResult.getAllErrors().getFirst().getDefaultMessage();

        log.warn("Validation Error: {}", errorMessage);

        return new ApiResponse<>(
                CommonResponseCode.VALIDATION_FAILED.getHttpStatus(),
                CommonResponseCode.VALIDATION_FAILED.getCode(),
                errorMessage,
                null
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ApiResponse<Void> handle(HandlerMethodValidationException e) {
        String errorMessage = e.getAllErrors().getFirst().getDefaultMessage();

        log.warn("Validation Error: {}", errorMessage);

        return new ApiResponse<>(
                CommonResponseCode.VALIDATION_FAILED.getHttpStatus(),
                CommonResponseCode.VALIDATION_FAILED.getCode(),
                errorMessage,
                null
        );
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            ServletRequestBindingException.class
    })
    public ApiResponse<Void> handleInvalidRequest(Exception e) {
        log.warn("Invalid Request: {}", e.getMessage());
        return ApiResponse.from(CommonResponseCode.INVALID_REQUEST);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResponse<Void> handle(NoResourceFoundException e) {
        log.warn("Resource Not Found: {}", e.getMessage());
        return ApiResponse.from(CommonResponseCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResponse<Void> handle(
            HttpRequestMethodNotSupportedException e,
            HttpServletResponse response
    ) {
        e.getHeaders().forEach((name, values) ->
                values.forEach(value -> response.addHeader(name, value))
        );

        log.warn("Method Not Allowed: {}", e.getMessage());
        return ApiResponse.from(CommonResponseCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ApiResponse<Void> handle(HttpMediaTypeNotSupportedException e) {
        log.warn("Unsupported Media Type: {}", e.getMessage());
        return ApiResponse.from(CommonResponseCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handle(Exception e) {
        log.error("Error: {}", e.getMessage(), e);
        return ApiResponse.from(CommonResponseCode.INTERNAL_SERVER_ERROR);
    }
}
