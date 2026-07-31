package com.ward.techview.voice.dto;

import com.ward.techview.interview.api.CreatedAnswer;
import com.ward.techview.voice.api.PresignedUpload;

import java.net.URI;
import java.time.OffsetDateTime;

public record AnswerCreateResponse(
        Long answerId,
        URI uploadUrl,
        OffsetDateTime uploadExpiresAt
) {
    public static AnswerCreateResponse of(CreatedAnswer answer, PresignedUpload upload) {
        return new AnswerCreateResponse(
                answer.id(),
                upload.uploadUrl(),
                upload.expiresAt()
        );
    }
}
