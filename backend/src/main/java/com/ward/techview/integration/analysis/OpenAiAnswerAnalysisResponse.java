package com.ward.techview.integration.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiAnswerAnalysisResponse(
        String status,
        String model,
        List<Output> output,
        @JsonProperty("incomplete_details")
        IncompleteDetails incompleteDetails
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Output(
            String type,
            List<Content> content
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(
            String type,
            String text,
            String refusal
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IncompleteDetails(
            String reason
    ) {
    }
}
