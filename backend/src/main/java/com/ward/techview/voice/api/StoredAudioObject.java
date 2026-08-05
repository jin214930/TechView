package com.ward.techview.voice.api;

import java.time.OffsetDateTime;

public record StoredAudioObject(
        String objectKey,
        String etag,
        OffsetDateTime lastModifiedAt
) {
    public StoredAudioObject {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("음성 객체 키는 필수입니다.");
        }

        if (etag == null || etag.isBlank()) {
            throw new IllegalArgumentException("음성 객체 ETag는 필수입니다.");
        }

        if (lastModifiedAt == null) {
            throw new IllegalArgumentException("음성 객체 수정 시각은 필수입니다.");
        }
    }
}
