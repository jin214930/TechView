package com.ward.techview.integration.feedback;

import java.util.List;
import java.util.Map;

final class InterviewFeedbackJsonSchema {
    static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "properties", Map.of(
                    "overallSummary", Map.of(
                            "type", "string"
                    ),
                    "strengths", feedbackArraySchema(),
                    "improvements", feedbackArraySchema(),
                    "categoryFeedbacks", Map.of(
                            "type", "array",
                            "minItems", 5,
                            "maxItems", 5,
                            "items", Map.of(
                                    "type", "object",
                                    "additionalProperties", false,
                                    "properties", Map.of(
                                            "category", Map.of(
                                                    "type", "string"
                                            ),
                                            "assessment", Map.of(
                                                    "type", "string"
                                            ),
                                            "recommendation", Map.of(
                                                    "type", "string"
                                            )
                                    ),
                                    "required", List.of(
                                            "category",
                                            "assessment",
                                            "recommendation"
                                    )
                            )
                    ),
                    "recommendedTopics", feedbackArraySchema()
            ),
            "required", List.of(
                    "overallSummary",
                    "strengths",
                    "improvements",
                    "categoryFeedbacks",
                    "recommendedTopics"
            )
    );

    private InterviewFeedbackJsonSchema() {
    }

    private static Map<String, Object> feedbackArraySchema() {
        return Map.of(
                "type", "array",
                "minItems", 1,
                "maxItems", 3,
                "items", Map.of("type", "string")
        );
    }
}
