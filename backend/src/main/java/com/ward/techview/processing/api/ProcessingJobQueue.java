package com.ward.techview.processing.api;

import java.util.Optional;

public interface ProcessingJobQueue {
    Optional<ProcessingJobReceipt> findSttJob(Long answerId);

    boolean hasSttJob(Long answerId);

    ProcessingJobReceipt createOrGetSttJob(
            Long interviewSessionId,
            Long answerId,
            String audioObjectKey,
            String sourceObjectEtag
    );

    ProcessingJobReceipt createOrGetAnswerAnalysisJob(Long interviewSessionId, Long answerId);
}
