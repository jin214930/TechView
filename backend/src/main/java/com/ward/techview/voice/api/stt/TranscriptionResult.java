package com.ward.techview.voice.api.stt;

public record TranscriptionResult(
        String transcript
) {
    public TranscriptionResult {
        if (transcript == null || transcript.isBlank()) {
            throw new IllegalArgumentException("변환된 텍스트는 비어 있을 수 없습니다.");
        }
    }
}
