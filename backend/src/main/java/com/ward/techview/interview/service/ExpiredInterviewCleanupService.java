package com.ward.techview.interview.service;

import com.ward.techview.interview.config.ExpiredInterviewCleanupProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class ExpiredInterviewCleanupService {
    private final ExpiredInterviewCleanupBatchService batchService;
    private final ExpiredInterviewCleanupProperties properties;
    private final InterviewRetentionPolicy retentionPolicy;

    public int cleanup() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime cutoff = retentionPolicy.calculateCutoff(now);

        long cursor = 0L;
        int totalDeletedCount = 0;

        while (true) {
            ExpiredInterviewCleanupBatchResult result = batchService.cleanupBatch(
                    now,
                    cutoff,
                    cursor,
                    properties.batchSize()
            );

            totalDeletedCount += result.deletedCount();

            if (result.scannedCount() == 0) {
                break;
            }

            cursor = result.nextCursor();

            if (result.scannedCount() < properties.batchSize()) {
                break;
            }
        }

        return totalDeletedCount;
    }
}
