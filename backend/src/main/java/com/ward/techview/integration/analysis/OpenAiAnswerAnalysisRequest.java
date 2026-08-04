package com.ward.techview.integration.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record OpenAiAnswerAnalysisRequest(
        String model,
        List<Input> input,
        @JsonProperty("max_output_tokens")
        int maxOutputTokens,
        boolean store,
        Text text
) {
    record Input(
            String role,
            String content
    ) {
    }

    record Text(
            Format format
    ) {
    }

    record Format(
            String type,
            String name,
            boolean strict,
            Map<String, Object> schema
    ) {
    }

    static OpenAiAnswerAnalysisRequest of(
            OpenAiAnswerAnalysisProperties properties,
            String systemPrompt,
            String userPrompt
    ) {
        return new OpenAiAnswerAnalysisRequest(
                properties.model(),
                List.of(
                        new Input("system", systemPrompt),
                        new Input("user", userPrompt)
                ),
                properties.maxOutputTokens(),
                false,
                new Text(
                        new Format(
                                "json_schema",
                                "answer_analysis",
                                true,
                                AnswerAnalysisJsonSchema.SCHEMA
                        )
                )
        );
    }
}
