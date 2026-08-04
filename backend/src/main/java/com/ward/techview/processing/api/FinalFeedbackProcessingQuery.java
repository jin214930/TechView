package com.ward.techview.processing.api;

public interface FinalFeedbackProcessingQuery {
    FinalFeedbackProcessingSnapshot getStatus(Long interviewSessionId);
}
