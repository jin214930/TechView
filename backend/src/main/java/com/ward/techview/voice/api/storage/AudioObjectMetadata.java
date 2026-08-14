package com.ward.techview.voice.api.storage;

public record AudioObjectMetadata(
        String contentType,
        long contentLength,
        String etag
) {
}
