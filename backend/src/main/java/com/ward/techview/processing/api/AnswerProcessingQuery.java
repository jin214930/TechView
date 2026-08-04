package com.ward.techview.processing.api;

public interface AnswerProcessingQuery {
    AnswerProcessingSnapshot getStatus(Long answerId);
}
