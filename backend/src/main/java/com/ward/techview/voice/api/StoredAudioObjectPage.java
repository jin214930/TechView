package com.ward.techview.voice.api;

import java.util.List;

public record StoredAudioObjectPage(
        List<StoredAudioObject> objects,
        String nextContinuationToken
) {
    public StoredAudioObjectPage {
        objects = List.copyOf(objects);

        if (nextContinuationToken != null && nextContinuationToken.isBlank()) {
            throw new IllegalArgumentException("다음 페이지 토큰은 비어 있을 수 없습니다.");
        }
    }

    public boolean hasNext() {
        return nextContinuationToken != null;
    }
}
