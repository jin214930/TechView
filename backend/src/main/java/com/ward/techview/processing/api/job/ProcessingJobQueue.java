package com.ward.techview.processing.api.job;

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

    ProcessingJobReceipt createOrGetFinalFeedbackJob(Long interviewSessionId);
}
