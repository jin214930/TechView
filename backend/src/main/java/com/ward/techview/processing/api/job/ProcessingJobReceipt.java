package com.ward.techview.processing.api.job;

public record ProcessingJobReceipt(
        Long processingJobId,
        Long interviewSessionId
) {
}
