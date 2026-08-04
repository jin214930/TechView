package com.ward.techview.integration.feedback;

import com.ward.techview.evaluation.api.*;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class OpenAiInterviewFeedbackProvider implements InterviewFeedbackProvider {
    private static final String RESPONSES_URI = "/v1/responses";
    private static final String COMPLETED_STATUS = "completed";
    private static final String MESSAGE_TYPE = "message";
    private static final String OUTPUT_TEXT_TYPE = "output_text";
    private static final String REFUSAL_TYPE = "refusal";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final OpenAiInterviewFeedbackProperties properties;

    public OpenAiInterviewFeedbackProvider(
            RestClient restClient,
            ObjectMapper objectMapper,
            OpenAiInterviewFeedbackProperties properties
    ) {
        this.restClient = Objects.requireNonNull(restClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public InterviewFeedbackResult generate(
            InterviewFeedbackPrompt prompt
    ) {
        Objects.requireNonNull(prompt);

        String promptJson = serializePrompt(prompt);

        OpenAiInterviewFeedbackRequest request =
                OpenAiInterviewFeedbackRequest.of(
                        properties,
                        InterviewFeedbackPromptFactory.systemPrompt(),
                        InterviewFeedbackPromptFactory.userPrompt(promptJson)
                );

        try {
            OpenAiInterviewFeedbackResponse response =
                    restClient.post()
                            .uri(RESPONSES_URI)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .body(OpenAiInterviewFeedbackResponse.class);

            return toResult(prompt, response);
        } catch (InterviewFeedbackException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw mapResponseException(exception);
        } catch (ResourceAccessException exception) {
            throw new InterviewFeedbackException(
                    InterviewFeedbackFailure.TEMPORARY_FAILURE,
                    "종합 피드백 공급자에 연결할 수 없습니다.",
                    exception
            );
        } catch (RestClientException exception) {
            throw new InterviewFeedbackException(
                    InterviewFeedbackFailure.UNKNOWN,
                    "종합 피드백 응답을 처리할 수 없습니다.",
                    exception
            );
        }
    }

    private String serializePrompt(
            InterviewFeedbackPrompt prompt
    ) {
        try {
            return objectMapper.writeValueAsString(prompt);
        } catch (JacksonException exception) {
            throw new InterviewFeedbackException(
                    InterviewFeedbackFailure.UNKNOWN,
                    "종합 피드백 입력을 직렬화할 수 없습니다.",
                    exception
            );
        }
    }

    private InterviewFeedbackResult toResult(
            InterviewFeedbackPrompt prompt,
            OpenAiInterviewFeedbackResponse response
    ) {
        validateResponse(response);

        String outputText = extractOutputText(response);
        FeedbackPayload payload = deserializePayload(outputText);

        try {
            InterviewFeedbackResult result =
                    new InterviewFeedbackResult(
                            payload.overallSummary(),
                            payload.strengths(),
                            payload.improvements(),
                            payload.categoryFeedbacks(),
                            payload.recommendedTopics(),
                            response.model(),
                            properties.promptVersion()
                    );

            validateCategories(prompt, result);

            return result;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidResponse(
                    "종합 피드백 결과가 도메인 제약을 만족하지 않습니다.",
                    exception
            );
        }
    }

    private void validateCategories(
            InterviewFeedbackPrompt prompt,
            InterviewFeedbackResult result
    ) {
        Set<String> expectedCategories = prompt.turns()
                .stream()
                .map(InterviewFeedbackTurnPrompt::category)
                .collect(Collectors.toSet());

        Set<String> actualCategories = result.categoryFeedbacks()
                .stream()
                .map(CategoryFeedback::category)
                .collect(Collectors.toSet());

        if (!expectedCategories.equals(actualCategories)) {
            throw invalidResponse("종합 피드백의 카테고리가 면접 카테고리와 일치하지 않습니다.", null);
        }
    }

    private void validateResponse(OpenAiInterviewFeedbackResponse response) {
        if (response == null) {
            throw invalidResponse("종합 피드백 공급자가 빈 응답을 반환했습니다.", null);
        }

        if (!COMPLETED_STATUS.equals(response.status())) {
            String reason = response.incompleteDetails() == null
                    ? null
                    : response.incompleteDetails().reason();

            throw invalidResponse("종합 피드백 생성이 완료되지 않았습니다. status=" + response.status() + ", reason=" + reason, null);
        }

        if (response.model() == null || response.model().isBlank()) {
            throw invalidResponse("종합 피드백 응답에 모델 정보가 없습니다.", null);
        }

        if (response.output() == null || response.output().isEmpty()) {
            throw invalidResponse(
                    "종합 피드백 응답에 출력 내용이 없습니다.",
                    null
            );
        }
    }

    private String extractOutputText(
            OpenAiInterviewFeedbackResponse response
    ) {
        for (OpenAiInterviewFeedbackResponse.Output output : response.output()) {
            if (output == null
                    || !MESSAGE_TYPE.equals(output.type())
                    || output.content() == null) {
                continue;
            }

            for (OpenAiInterviewFeedbackResponse.Content content : output.content()) {
                if (content == null) {
                    continue;
                }

                if (REFUSAL_TYPE.equals(content.type())) {
                    throw invalidResponse(
                            "종합 피드백 공급자가 요청 처리를 거절했습니다.",
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
                "종합 피드백 응답에 유효한 출력 텍스트가 없습니다.",
                null
        );
    }

    private FeedbackPayload deserializePayload(String outputText) {
        try {
            return objectMapper.readValue(
                    outputText,
                    FeedbackPayload.class
            );
        } catch (JacksonException exception) {
            throw invalidResponse(
                    "종합 피드백 결과를 역직렬화할 수 없습니다.",
                    exception
            );
        }
    }

    private InterviewFeedbackException mapResponseException(
            RestClientResponseException exception
    ) {
        InterviewFeedbackFailure failure =
                switch (exception.getStatusCode().value()) {
                    case 400, 404, 413, 415, 422 -> InterviewFeedbackFailure.INVALID_RESPONSE;

                    case 401, 403 -> InterviewFeedbackFailure.AUTHENTICATION_FAILED;

                    case 429 -> InterviewFeedbackFailure.RATE_LIMITED;

                    case 408, 409, 500, 502, 503, 504 -> InterviewFeedbackFailure.TEMPORARY_FAILURE;

                    default -> InterviewFeedbackFailure.UNKNOWN;
                };

        return new InterviewFeedbackException(
                failure,
                "종합 피드백 요청 처리에 실패했습니다. status="
                        + exception.getStatusCode().value(),
                exception
        );
    }

    private InterviewFeedbackException invalidResponse(
            String message,
            Throwable cause
    ) {
        return new InterviewFeedbackException(
                InterviewFeedbackFailure.INVALID_RESPONSE,
                message,
                cause
        );
    }

    private record FeedbackPayload(
            String overallSummary,
            List<String> strengths,
            List<String> improvements,
            List<CategoryFeedback> categoryFeedbacks,
            List<String> recommendedTopics
    ) {
    }
}
