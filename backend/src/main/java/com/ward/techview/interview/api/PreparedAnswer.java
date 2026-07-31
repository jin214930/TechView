package com.ward.techview.interview.api;

public record PreparedAnswer(
        Long id,
        String audioObjectKey,
        String audioContentType,
        boolean created
) {
}
