package com.ward.techview.interview.api.feedback;

import java.util.List;

public record InterviewFeedbackContext(
        Long interviewSessionId,
        List<InterviewFeedbackTurnContext> turns
) {
}
