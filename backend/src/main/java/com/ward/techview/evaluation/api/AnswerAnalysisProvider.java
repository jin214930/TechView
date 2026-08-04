package com.ward.techview.evaluation.api;

public interface AnswerAnalysisProvider {
    AnswerAnalysisResult analyze(AnswerAnalysisPrompt prompt);
}
