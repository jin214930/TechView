package com.ward.techview.voice.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TranscriptionResultTest {

    @Test
    @DisplayName("변환된 텍스트로 STT 결과를 생성한다")
    void createsTranscriptionResult() {
        assertDoesNotThrow(() -> new TranscriptionResult("안녕하세요"));
    }

    @Test
    @DisplayName("빈 텍스트로 STT 결과를 생성할 수 없다")
    void rejectsBlankTranscript() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TranscriptionResult(" ")
        );
    }
}
