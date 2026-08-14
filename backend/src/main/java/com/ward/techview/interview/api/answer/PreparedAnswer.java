package com.ward.techview.interview.api.answer;

public record PreparedAnswer(
        Long id,
        String audioObjectKey,
        String audioContentType,
        boolean created
) {
}
