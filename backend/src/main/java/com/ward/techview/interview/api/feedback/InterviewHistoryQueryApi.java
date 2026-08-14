package com.ward.techview.interview.api.feedback;

import org.springframework.data.domain.Page;

public interface InterviewHistoryQueryApi {
    Page<CompletedInterviewSummary> getCompletedInterviews(Long memberId, int page, int size);
}
