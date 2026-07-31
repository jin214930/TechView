package com.ward.techview.voice.response;

import com.ward.techview.common.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VoiceResponseCode implements ResponseCode {
    UNSUPPORTED_AUDIO_CONTENT_TYPE(4000, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 음성 형식입니다."),
    AUDIO_DURATION_INVALID(4001, HttpStatus.BAD_REQUEST, "음성 재생 시간이 올바르지 않습니다.");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;
}
