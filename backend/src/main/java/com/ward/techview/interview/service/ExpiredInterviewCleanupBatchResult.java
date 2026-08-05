package com.ward.techview.interview.service;

public record ExpiredInterviewCleanupBatchResult(
        long nextCursor,
        int scannedCount,
        int deletedCount
) {
    public static ExpiredInterviewCleanupBatchResult empty(long currentCursor) {
        return new ExpiredInterviewCleanupBatchResult(
                currentCursor,
                0,
                0
        );
    }
}
