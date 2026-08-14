package com.ward.techview.interview.api.feedback;

public interface InterviewFeedbackAccessApi {
    void validateCompletedInterview(Long memberId, Long interviewSessionId);
}
