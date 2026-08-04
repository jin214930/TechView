package com.ward.techview.processing.api;

public record AnswerProcessingSnapshot(
        AnswerProcessingStatus status,
        String errorCode
) {
    public static AnswerProcessingSnapshot notStarted() {
        return new AnswerProcessingSnapshot(AnswerProcessingStatus.NOT_STARTED, null);
    }

    public static AnswerProcessingSnapshot processing() {
        return new AnswerProcessingSnapshot(AnswerProcessingStatus.PROCESSING, null);
    }

    public static AnswerProcessingSnapshot completed() {
        return new AnswerProcessingSnapshot(AnswerProcessingStatus.COMPLETED, null);
    }

    public static AnswerProcessingSnapshot failed(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("실패 코드가 필요합니다.");
        }

        return new AnswerProcessingSnapshot(AnswerProcessingStatus.FAILED, errorCode);
    }
}
