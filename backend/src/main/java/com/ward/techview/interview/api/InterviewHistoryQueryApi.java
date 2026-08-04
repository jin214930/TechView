package com.ward.techview.interview.api;

import org.springframework.data.domain.Page;

public interface InterviewHistoryQueryApi {
    Page<CompletedInterviewSummary> getCompletedInterviews(Long memberId, int page, int size);
}
