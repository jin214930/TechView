package com.ward.techview.integration.analysis;

import com.ward.techview.evaluation.api.*;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

import java.util.List;
import java.util.Objects;

public class OpenAiAnswerAnalysisProvider implements AnswerAnalysisProvider {
    private static final String RESPONSES_URI = "/v1/responses";
    private static final String COMPLETED_STATUS = "completed";
    private static final String MESSAGE_TYPE = "message";
    private static final String OUTPUT_TEXT_TYPE = "output_text";
    private static final String REFUSAL_TYPE = "refusal";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final OpenAiAnswerAnalysisProperties properties;

    public OpenAiAnswerAnalysisProvider(
            RestClient restClient,
            ObjectMapper objectMapper,
            OpenAiAnswerAnalysisProperties properties
    ) {
        this.restClient = Objects.requireNonNull(restClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public AnswerAnalysisResult analyze(AnswerAnalysisPrompt prompt) {
        Objects.requireNonNull(prompt);

        OpenAiAnswerAnalysisRequest request = OpenAiAnswerAnalysisRequest.of(
                properties,
                AnswerAnalysisPromptFactory.systemPrompt(),
                AnswerAnalysisPromptFactory.userPrompt(prompt)
        );

        try {
            OpenAiAnswerAnalysisResponse response = restClient.post()
                    .uri(RESPONSES_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(OpenAiAnswerAnalysisResponse.class);

            return toResult(response);
        } catch (AnswerAnalysisException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw mapResponseException(exception);
        } catch (ResourceAccessException exception) {
            throw new AnswerAnalysisException(
                    AnswerAnalysisFailure.TEMPORARY_FAILURE,
                    "답변 분석 공급자에 연결할 수 없습니다.",
                    exception
            );
        } catch (RestClientException exception) {
            throw new AnswerAnalysisException(
                    AnswerAnalysisFailure.UNKNOWN,
                    "답변 분석 응답을 처리할 수 없습니다.",
                    exception
            );
        }
    }

    private AnswerAnalysisResult toResult(OpenAiAnswerAnalysisResponse response) {
        validateResponse(response);

        String outputText = extractOutputText(response);
        AnalysisPayload payload = deserializePayload(outputText);

        try {
            return new AnswerAnalysisResult(
                    payload.summary(),
                    payload.strengths(),
                    payload.improvements(),
                    payload.followUpDecision(),
                    payload.followUpReason(),
                    payload.followUpQuestion(),
                    response.model(),
                    properties.promptVersion()
            );
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidResponse("답변 분석 결과가 도메인 제약을 만족하지 않습니다.", exception);
        }
    }

    private void validateResponse(OpenAiAnswerAnalysisResponse response) {
        if (response == null) {
            throw invalidResponse("답변 분석 공급자가 빈 응답을 반환했습니다.", null);
        }

        if (!COMPLETED_STATUS.equals(response.status())) {
            String reason = response.incompleteDetails() == null
                    ? null
                    : response.incompleteDetails().reason();

            throw invalidResponse("답변 분석이 완료되지 않았습니다. status=" + response.status() + ", reason=" + reason, null);
        }

        if (response.model() == null || response.model().isBlank()) {
            throw invalidResponse("답변 분석 응답에 모델 정보가 없습니다.", null);
        }

        if (response.output() == null || response.output().isEmpty()) {
            throw invalidResponse(
                    "답변 분석 응답에 출력 내용이 없습니다.",
                    null
            );
        }
    }

    private String extractOutputText(
            OpenAiAnswerAnalysisResponse response
    ) {
        for (OpenAiAnswerAnalysisResponse.Output output : response.output()) {
            if (output == null
                    || !MESSAGE_TYPE.equals(output.type())
                    || output.content() == null) {
                continue;
            }

            for (OpenAiAnswerAnalysisResponse.Content content : output.content()) {
                if (content == null) {
                    continue;
                }

                if (REFUSAL_TYPE.equals(content.type())) {
                    throw invalidResponse(
                            "답변 분석 공급자가 요청 처리를 거절했습니다.",
                            null
                    );
                }

                if (OUTPUT_TEXT_TYPE.equals(content.type())
                        && content.text() != null
                        && !content.text().isBlank()) {
                    return content.text();
                }
            }
        }

        throw invalidResponse(
                "답변 분석 응답에 유효한 출력 텍스트가 없습니다.",
                null
        );
    }

    private AnalysisPayload deserializePayload(String outputText) {
        try {
            return objectMapper.readValue(
                    outputText,
                    AnalysisPayload.class
            );
        } catch (JacksonException exception) {
            throw invalidResponse(
                    "답변 분석 결과를 역직렬화할 수 없습니다.",
                    exception
            );
        }
    }

    private AnswerAnalysisException mapResponseException(
            RestClientResponseException exception
    ) {
        AnswerAnalysisFailure failure =
                switch (exception.getStatusCode().value()) {
                    case 400, 404, 413, 415, 422 -> AnswerAnalysisFailure.INVALID_RESPONSE;

                    case 401, 403 -> AnswerAnalysisFailure.AUTHENTICATION_FAILED;

                    case 429 -> AnswerAnalysisFailure.RATE_LIMITED;

                    case 408, 409, 500, 502, 503, 504 -> AnswerAnalysisFailure.TEMPORARY_FAILURE;

                    default -> AnswerAnalysisFailure.UNKNOWN;
                };

        return new AnswerAnalysisException(
                failure,
                "답변 분석 요청 처리에 실패했습니다. status="
                        + exception.getStatusCode().value(),
                exception
        );
    }

    private AnswerAnalysisException invalidResponse(
            String message,
            Throwable cause
    ) {
        return new AnswerAnalysisException(
                AnswerAnalysisFailure.INVALID_RESPONSE,
                message,
                cause
        );
    }

    private record AnalysisPayload(
            String summary,
            List<String> strengths,
            List<String> improvements,
            FollowUpDecision followUpDecision,
            String followUpReason,
            String followUpQuestion
    ) {
    }
}
