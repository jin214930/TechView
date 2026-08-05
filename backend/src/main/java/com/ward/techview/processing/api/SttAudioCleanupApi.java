package com.ward.techview.processing.api;

import java.time.OffsetDateTime;
import java.util.List;

public interface SttAudioCleanupApi {
    List<FailedAudioCleanupTarget> findDueTargets(OffsetDateTime now, Long afterProcessingJobId, int limit);

    boolean markAudioDeleted(Long processingJobId, OffsetDateTime deletedAt);
}
