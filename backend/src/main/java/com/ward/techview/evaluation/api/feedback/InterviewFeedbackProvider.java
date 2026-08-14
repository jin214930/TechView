package com.ward.techview.evaluation.api.feedback;

public interface InterviewFeedbackProvider {
    InterviewFeedbackResult generate(InterviewFeedbackPrompt prompt);
}
