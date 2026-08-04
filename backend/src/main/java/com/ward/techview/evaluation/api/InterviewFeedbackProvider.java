package com.ward.techview.evaluation.api;

public interface InterviewFeedbackProvider {
    InterviewFeedbackResult generate(InterviewFeedbackPrompt prompt);
}
