package com.ward.techview.voice.dto;

import com.ward.techview.processing.api.ProcessingJobReceipt;

public record AnswerUploadCompletionResponse(
        Long answerId,
        Long processingJobId
) {
    public static AnswerUploadCompletionResponse from(ProcessingJobReceipt job) {
        return new AnswerUploadCompletionResponse(job.answerId(), job.processingJobId());
    }
}
