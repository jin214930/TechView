package com.ward.techview.voice.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AudioObjectTest {

    @Test
    @DisplayName("음성 데이터와 Content-Type으로 음성 객체를 생성한다")
    void createsAudioObject() {
        assertDoesNotThrow(
                () -> new AudioObject(new byte[]{1}, "audio/webm")
        );
    }

    @Test
    @DisplayName("빈 음성 데이터로 음성 객체를 생성할 수 없다")
    void rejectsEmptyAudioContent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AudioObject(new byte[0], "audio/webm")
        );
    }

    @Test
    @DisplayName("Content-Type이 없으면 음성 객체를 생성할 수 없다")
    void rejectsBlankContentType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AudioObject(new byte[]{1}, " ")
        );
    }
}
