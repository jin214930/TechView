package com.ward.techview.processing.api.feedback;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface FinalFeedbackJobControl {
    Optional<ClaimedFinalFeedbackJob> claim(Long processingJobId);

    boolean succeed(Long processingJobId, int attemptCount);

    boolean retry(
            Long processingJobId,
            int attemptCount,
            OffsetDateTime nextAttemptAt,
            String errorCode,
            String errorMessage
    );

    boolean fail(
            Long processingJobId,
            int attemptCount,
            String errorCode,
            String errorMessage
    );
}
