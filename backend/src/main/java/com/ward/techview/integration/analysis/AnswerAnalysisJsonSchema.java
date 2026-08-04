package com.ward.techview.integration.analysis;

import java.util.List;
import java.util.Map;

final class AnswerAnalysisJsonSchema {
    static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "properties", Map.of(
                    "summary", Map.of(
                            "type", "string"
                    ),
                    "strengths", Map.of(
                            "type", "array",
                            "items", Map.of("type", "string"),
                            "maxItems", 3
                    ),
                    "improvements", Map.of(
                            "type", "array",
                            "items", Map.of("type", "string"),
                            "maxItems", 3
                    ),
                    "followUpDecision", Map.of(
                            "type", "string",
                            "enum", List.of("ASK", "SKIP")
                    ),
                    "followUpReason", Map.of(
                            "type", "string"
                    ),
                    "followUpQuestion", Map.of(
                            "type", List.of("string", "null")
                    )
            ),
            "required", List.of(
                    "summary",
                    "strengths",
                    "improvements",
                    "followUpDecision",
                    "followUpReason",
                    "followUpQuestion"
            )
    );

    private AnswerAnalysisJsonSchema() {
    }
}
