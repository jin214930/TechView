package com.ward.techview.interview.api;

public record AnswerAnalysisContext(
        Long interviewSessionId,
        Long answerId,
        String category,
        String coreQuestion,
        String followUpQuestion,
        String transcript
) {
    public boolean followUpAllowed() {
        return followUpQuestion == null;
    }
}
