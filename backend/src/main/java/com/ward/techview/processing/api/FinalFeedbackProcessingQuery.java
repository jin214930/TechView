package com.ward.techview.processing.api;

import java.util.Map;
import java.util.Set;

public interface FinalFeedbackProcessingQuery {
    FinalFeedbackProcessingSnapshot getStatus(Long interviewSessionId);

    Map<Long, FinalFeedbackProcessingSnapshot> getStatuses(Set<Long> interviewSessionIds);
}
