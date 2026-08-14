package com.ward.techview.voice.api.storage;

import java.util.Objects;

public record AudioObject(
        byte[] content,
        String contentType
) {
    public AudioObject {
        Objects.requireNonNull(content, "음성 데이터는 필수입니다.");

        if (content.length == 0) {
            throw new IllegalArgumentException("음성 데이터는 비어 있을 수 없습니다.");
        }

        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("음성 Content-Type은 필수입니다.");
        }
    }
}
