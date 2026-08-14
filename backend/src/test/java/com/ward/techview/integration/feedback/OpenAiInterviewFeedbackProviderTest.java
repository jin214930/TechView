package com.ward.techview.integration.feedback;

import com.ward.techview.evaluation.api.feedback.CategoryFeedback;
import com.ward.techview.evaluation.api.feedback.InterviewFeedbackException;
import com.ward.techview.evaluation.api.feedback.InterviewFeedbackFailure;
import com.ward.techview.evaluation.api.feedback.InterviewFeedbackPrompt;
import com.ward.techview.evaluation.api.feedback.InterviewFeedbackResult;
import com.ward.techview.evaluation.api.feedback.InterviewFeedbackTurnPrompt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiInterviewFeedbackProviderTest {

    private static final String BASE_URL = "https://api.openai.com";
    private static final String MODEL = "gpt-5.6-luna";
    private static final String PROMPT_VERSION = "interview-feedback-v1";

    private MockRestServiceServer server;
    private OpenAiInterviewFeedbackProvider provider;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        objectMapper = new ObjectMapper();

        OpenAiInterviewFeedbackProperties properties =
                new OpenAiInterviewFeedbackProperties(
                        MODEL,
                        PROMPT_VERSION,
                        2000,
                        Duration.ofSeconds(90)
                );

        provider = new OpenAiInterviewFeedbackProvider(
                builder.baseUrl(BASE_URL).build(),
                objectMapper,
                properties
        );
    }

    @Test
    @DisplayName("종합 피드백 요청을 전송하고 구조화 결과를 변환한다")
    void generatesAndMapsInterviewFeedback() throws JacksonException {
        server.expect(once(), requestTo(BASE_URL + "/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.max_output_tokens").value(2000))
                .andExpect(jsonPath("$.text.format.name").value("interview_feedback"))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andExpect(jsonPath("$.text.format.schema.properties.categoryFeedbacks.minItems").value(5))
                .andExpect(jsonPath("$.input[1].content").value(containsString("다형성")))
                .andRespond(withSuccess(
                        completedResponse(feedbackPayload(categories())),
                        MediaType.APPLICATION_JSON
                ));

        InterviewFeedbackResult result = provider.generate(prompt());

        assertAll(
                () -> assertEquals("전반적으로 핵심 개념을 이해하고 있습니다.", result.overallSummary()),
                () -> assertEquals(List.of("핵심 개념을 명확하게 설명했습니다."), result.strengths()),
                () -> assertEquals(List.of("구체적인 사례를 보강할 필요가 있습니다."), result.improvements()),
                () -> assertEquals(categories(), result.categoryFeedbacks().stream().map(CategoryFeedback::category).toList()),
                () -> assertEquals(List.of("Java 동시성"), result.recommendedTopics()),
                () -> assertEquals(MODEL, result.model()),
                () -> assertEquals(PROMPT_VERSION, result.promptVersion()),
                server::verify
        );
    }

    @Test
    @DisplayName("입력과 다른 카테고리를 반환한 종합 피드백을 거부한다")
    void rejectsMismatchedCategories() throws JacksonException {
        List<String> mismatchedCategories = List.of(
                "JAVA",
                "SPRING",
                "DATABASE",
                "OPERATING_SYSTEM",
                "SECURITY"
        );
        server.expect(once(), requestTo(BASE_URL + "/v1/responses"))
                .andRespond(withSuccess(
                        completedResponse(feedbackPayload(mismatchedCategories)),
                        MediaType.APPLICATION_JSON
                ));

        assertFailure(InterviewFeedbackFailure.INVALID_RESPONSE);
    }

    @Test
    @DisplayName("불완전한 응답을 유효하지 않은 종합 피드백으로 변환한다")
    void rejectsIncompleteResponse() {
        server.expect(once(), requestTo(BASE_URL + "/v1/responses"))
                .andRespond(withSuccess(
                        """
                                {
                                  "status": "incomplete",
                                  "model": "gpt-5.6-luna",
                                  "output": [],
                                  "incomplete_details": {
                                    "reason": "max_output_tokens"
                                  }
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        assertFailure(InterviewFeedbackFailure.INVALID_RESPONSE);
    }

    @Test
    @DisplayName("모델의 거절 응답을 유효하지 않은 종합 피드백으로 변환한다")
    void rejectsRefusalResponse() {
        server.expect(once(), requestTo(BASE_URL + "/v1/responses"))
                .andRespond(withSuccess(
                        """
                                {
                                  "status": "completed",
                                  "model": "gpt-5.6-luna",
                                  "output": [
                                    {
                                      "type": "message",
                                      "content": [
                                        {
                                          "type": "refusal",
                                          "refusal": "요청을 처리할 수 없습니다."
                                        }
                                      ]
                                    }
                                  ]
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        assertFailure(InterviewFeedbackFailure.INVALID_RESPONSE);
    }

    @Test
    @DisplayName("종합 피드백 JSON이 잘못되면 유효하지 않은 응답으로 변환한다")
    void rejectsMalformedFeedbackPayload() throws JacksonException {
        server.expect(once(), requestTo(BASE_URL + "/v1/responses"))
                .andRespond(withSuccess(
                        completedResponse("not-json"),
                        MediaType.APPLICATION_JSON
                ));

        assertFailure(InterviewFeedbackFailure.INVALID_RESPONSE);
    }

    @Test
    @DisplayName("도메인 제약을 위반한 종합 피드백 결과를 거부한다")
    void rejectsFeedbackPayloadViolatingDomainConstraint() throws JacksonException {
        Map<String, Object> payload = feedbackPayload(categories());
        payload = Map.of(
                "overallSummary", payload.get("overallSummary"),
                "strengths", List.of(),
                "improvements", payload.get("improvements"),
                "categoryFeedbacks", payload.get("categoryFeedbacks"),
                "recommendedTopics", payload.get("recommendedTopics")
        );
        server.expect(once(), requestTo(BASE_URL + "/v1/responses"))
                .andRespond(withSuccess(
                        completedResponse(payload),
                        MediaType.APPLICATION_JSON
                ));

        assertFailure(InterviewFeedbackFailure.INVALID_RESPONSE);
    }

    @ParameterizedTest
    @MethodSource("httpFailureCases")
    @DisplayName("OpenAI HTTP 오류를 종합 피드백 실패 유형으로 변환한다")
    void mapsHttpStatusToFeedbackFailure(
            int status,
            InterviewFeedbackFailure expectedFailure
    ) {
        server.expect(once(), requestTo(BASE_URL + "/v1/responses"))
                .andRespond(withRawStatus(status));

        assertFailure(expectedFailure);
    }

    private void assertFailure(InterviewFeedbackFailure expectedFailure) {
        InterviewFeedbackException exception = assertThrows(
                InterviewFeedbackException.class,
                () -> provider.generate(prompt())
        );

        assertAll(
                () -> assertSame(expectedFailure, exception.getFailure()),
                server::verify
        );
    }

    private String completedResponse(Map<String, Object> payload) throws JacksonException {
        return completedResponse(objectMapper.writeValueAsString(payload));
    }

    private String completedResponse(String outputText) throws JacksonException {
        return objectMapper.writeValueAsString(Map.of(
                "status", "completed",
                "model", MODEL,
                "output", List.of(Map.of(
                        "type", "message",
                        "content", List.of(Map.of(
                                "type", "output_text",
                                "text", outputText
                        ))
                ))
        ));
    }

    private Map<String, Object> feedbackPayload(List<String> feedbackCategories) {
        List<Map<String, Object>> categoryFeedbacks = feedbackCategories.stream()
                .map(category -> Map.<String, Object>of(
                        "category", category,
                        "assessment", category + " 개념을 이해하고 있습니다.",
                        "recommendation", category + " 심화 내용을 학습해 보세요."
                ))
                .toList();

        return Map.of(
                "overallSummary", "전반적으로 핵심 개념을 이해하고 있습니다.",
                "strengths", List.of("핵심 개념을 명확하게 설명했습니다."),
                "improvements", List.of("구체적인 사례를 보강할 필요가 있습니다."),
                "categoryFeedbacks", categoryFeedbacks,
                "recommendedTopics", List.of("Java 동시성")
        );
    }

    private InterviewFeedbackPrompt prompt() {
        List<InterviewFeedbackTurnPrompt> turns = List.of(
                turn(1, 10L, "JAVA", "다형성을 설명해 주세요."),
                turn(2, 20L, "SPRING", "DI를 설명해 주세요."),
                turn(3, 30L, "DATABASE", "인덱스를 설명해 주세요."),
                turn(4, 40L, "OPERATING_SYSTEM", "프로세스와 스레드를 설명해 주세요."),
                turn(5, 50L, "NETWORK", "TCP를 설명해 주세요.")
        );

        return new InterviewFeedbackPrompt(1L, turns);
    }

    private InterviewFeedbackTurnPrompt turn(
            int turnOrder,
            Long answerId,
            String category,
            String question
    ) {
        return new InterviewFeedbackTurnPrompt(
                answerId,
                turnOrder,
                category,
                question,
                null,
                "답변 내용",
                "답변 분석 요약",
                List.of("분석 강점"),
                List.of("분석 개선점")
        );
    }

    private static List<String> categories() {
        return List.of(
                "JAVA",
                "SPRING",
                "DATABASE",
                "OPERATING_SYSTEM",
                "NETWORK"
        );
    }

    private static Stream<Arguments> httpFailureCases() {
        return Stream.of(
                Arguments.of(401, InterviewFeedbackFailure.AUTHENTICATION_FAILED),
                Arguments.of(429, InterviewFeedbackFailure.RATE_LIMITED),
                Arguments.of(500, InterviewFeedbackFailure.TEMPORARY_FAILURE)
        );
    }
}
