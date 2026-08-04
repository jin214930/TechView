package com.ward.techview.interview.api;

public interface InterviewEvaluationContextApi {
    AnswerAnalysisContext getAnalysisContext(Long interviewSessionId, Long answerId);

    InterviewFeedbackContext getFinalFeedbackContext(Long interviewSessionId);
}
