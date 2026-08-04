package com.ward.techview.interview.api;

public interface InterviewFeedbackAccessApi {
    void validateCompletedInterview(Long memberId, Long interviewSessionId);
}
