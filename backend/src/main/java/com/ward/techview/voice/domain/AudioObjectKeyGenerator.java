package com.ward.techview.voice.domain;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AudioObjectKeyGenerator {
    private static final String KEY_FORMAT = "interviews/%d/turns/%d/%s";

    public String generate(Long sessionId, Long turnId) {
        return KEY_FORMAT.formatted(sessionId, turnId, UUID.randomUUID());
    }
}
