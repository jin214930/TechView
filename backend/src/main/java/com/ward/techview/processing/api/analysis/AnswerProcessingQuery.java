package com.ward.techview.processing.api.analysis;

public interface AnswerProcessingQuery {
    AnswerProcessingSnapshot getStatus(Long answerId);
}
