package com.ward.techview.processing.api.stt;

import com.ward.techview.processing.api.cleanup.FailedAudioCleanupTarget;

import java.time.OffsetDateTime;
import java.util.List;

public interface SttAudioCleanupApi {
    List<FailedAudioCleanupTarget> findDueTargets(OffsetDateTime now, Long afterProcessingJobId, int limit);

    boolean markAudioDeleted(Long processingJobId, OffsetDateTime deletedAt);
}
