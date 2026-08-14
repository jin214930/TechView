package com.ward.techview.processing.api.cleanup;

import java.util.Objects;

public record FailedAudioCleanupTarget(
        Long processingJobId,
        String audioObjectKey,
        String sourceObjectEtag
) {
    public FailedAudioCleanupTarget {
        processingJobId = Objects.requireNonNull(processingJobId, "Processing Job ID는 필수입니다.");

        if (audioObjectKey == null || audioObjectKey.isBlank()) {
            throw new IllegalArgumentException("음성 객체 키는 필수입니다.");
        }

        if (sourceObjectEtag == null || sourceObjectEtag.isBlank()) {
            throw new IllegalArgumentException("음성 객체 ETag는 필수입니다.");
        }
    }
}
