package com.ward.techview.processing.api;

import java.time.OffsetDateTime;

public interface SessionProcessingCleanupApi {
    boolean canDeleteSession(Long interviewSessionId, OffsetDateTime now);
}
