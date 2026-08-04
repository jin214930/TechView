package com.ward.techview.integration.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record OpenAiInterviewFeedbackRequest(
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

    static OpenAiInterviewFeedbackRequest of(
            OpenAiInterviewFeedbackProperties properties,
            String systemPrompt,
            String userPrompt
    ) {
        return new OpenAiInterviewFeedbackRequest(
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
                                "interview_feedback",
                                true,
                                InterviewFeedbackJsonSchema.SCHEMA
                        )
                )
        );
    }
}
