package com.ward.techview.evaluation.api.analysis;

public interface AnswerAnalysisProvider {
    AnswerAnalysisResult analyze(AnswerAnalysisPrompt prompt);
}
