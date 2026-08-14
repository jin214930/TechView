package com.ward.techview.interview.api.feedback;

import com.ward.techview.interview.api.answer.AnswerAnalysisContext;

public interface InterviewEvaluationContextApi {
    AnswerAnalysisContext getAnalysisContext(Long interviewSessionId, Long answerId);

    InterviewFeedbackContext getFinalFeedbackContext(Long interviewSessionId);
}
