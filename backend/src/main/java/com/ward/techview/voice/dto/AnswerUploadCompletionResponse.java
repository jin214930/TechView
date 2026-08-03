package com.ward.techview.voice.dto;

import com.ward.techview.processing.api.ProcessingJobReceipt;

public record AnswerUploadCompletionResponse(
        Long answerId,
        Long processingJobId
) {
    public static AnswerUploadCompletionResponse from(Long answerId, ProcessingJobReceipt job) {
        return new AnswerUploadCompletionResponse(answerId, job.processingJobId());
    }
}
