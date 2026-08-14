package com.ward.techview.processing.api.stt;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface SttJobControl {
    Optional<ClaimedSttJob> claim(Long processingJobId);

    boolean advanceToCleanup(Long processingJobId, int attemptCount);

    boolean succeed(Long processingJobId, int attemptCount);

    boolean retry(Long processingJobId, int attemptCount, OffsetDateTime nextAttemptAt, String errorCode, String errorMessage);

    boolean fail(Long processingJobId, int attemptCount, String errorCode, String errorMessage);
}
