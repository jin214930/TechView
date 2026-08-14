package com.ward.techview.voice.dto;

import com.ward.techview.interview.api.answer.PreparedAnswer;
import com.ward.techview.voice.api.storage.PresignedUpload;

import java.net.URI;
import java.time.OffsetDateTime;

public record AnswerCreateResponse(
        Long answerId,
        boolean created,
        URI uploadUrl,
        OffsetDateTime uploadExpiresAt
) {
    public static AnswerCreateResponse of(PreparedAnswer answer, PresignedUpload upload) {
        return new AnswerCreateResponse(
                answer.id(),
                answer.created(),
                upload.uploadUrl(),
                upload.expiresAt()
        );
    }
}
