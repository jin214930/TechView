package com.ward.techview.voice.response;

import com.ward.techview.common.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VoiceResponseCode implements ResponseCode {
    ANSWER_UPLOAD_ACCEPTED(0, HttpStatus.ACCEPTED, "답변 음성 처리가 접수되었습니다."),
    UNSUPPORTED_AUDIO_CONTENT_TYPE(4000, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 음성 형식입니다."),
    AUDIO_DURATION_INVALID(4001, HttpStatus.BAD_REQUEST, "음성 재생 시간이 올바르지 않습니다."),
    AUDIO_OBJECT_NOT_FOUND(4002, HttpStatus.CONFLICT, "업로드된 음성 파일을 찾을 수 없습니다."),
    AUDIO_CONTENT_TYPE_MISMATCH(4003, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "업로드된 음성 형식이 요청 정보와 일치하지 않습니다."),
    AUDIO_FILE_EMPTY(4004, HttpStatus.BAD_REQUEST, "업로드된 음성 파일이 비어 있습니다."),
    AUDIO_FILE_SIZE_EXCEEDED(4005, HttpStatus.CONTENT_TOO_LARGE, "업로드된 음성 파일이 허용 크기를 초과했습니다.");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;
}
