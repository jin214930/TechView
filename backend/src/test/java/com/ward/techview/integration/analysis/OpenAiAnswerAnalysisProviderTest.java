package com.ward.techview.integration.analysis;

import com.ward.techview.evaluation.api.analysis.AnswerAnalysisException;
import com.ward.techview.evaluation.api.analysis.AnswerAnalysisFailure;
import com.ward.techview.evaluation.api.analysis.AnswerAnalysisPrompt;
import com.ward.techview.evaluation.api.analysis.AnswerAnalysisResult;
import com.ward.techview.evaluation.api.analysis.FollowUpDecision;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiAnswerAnalysisProviderTest {

    private static final String BASE_URL = "https://api.openai.com";
    private static final String MODEL = "gpt-5.6-luna";
    private static final String PROMPT_VERSION = "answer-analysis-v1";

    private MockRestServiceServer server;
    private OpenAiAnswerAnalysisProvider provider;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        objectMapper = new ObjectMapper();

        OpenAiAnswerAnalysisProperties properties = new OpenAiAnswerAnalysisProperties(
                MODEL,
                PROMPT_VERSION,
                1000,
                Duration.ofSeconds(60)
        );

        provider = new OpenAiAnswerAnalysisProvider(
                builder.baseUrl(BASE_URL).build(),
                objectMapper,
                properties
        );
    }

    @Test
    @DisplayName("답변 분석 요청을 전송하고 ASK 결과를 변환한다")
    void analyzesAnswerAndMapsAskResult() throws JacksonException {
        server.expect(once(), requestTo(BASE_URL + "/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.max_output_tokens").value(1000))
                .andExpect(jsonPath("$.text.format.type").value("json_schema"))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andExpect(jsonPath("$.input[1].content").value(containsString("동시성")))
                .andRespond(withSuccess(
                        completedResponse(analysisPayload("ASK", "volatile은 무엇을 보장하나요?")),
                        MediaType.APPLICATION_JSON
                ));

        AnswerAnalysisResult result = provider.analyze(coreAnswerPrompt());

        assertAll(
                () -> assertEquals("핵심 개념을 설명했지만 일부 보완이 필요합니다.", result.summary()),
                () -> assertEquals(List.of("경쟁 상태를 언급했습니다."), result.strengths()),
                () -> assertEquals(List.of("가시성 설명이 필요합니다."), result.improvements()),
                () -> assertSame(FollowUpDecision.ASK, result.followUpDecision()),
                () -> assertEquals("가시성 이해를 확인해야 합니다.", result.followUpReason()),
                () -> assertEquals("volatile은 무엇을 보장하나요?", result.followUpQuestion()),
                () -> assertEquals(MODEL, result.model()),
                () -> assertEquals(PROMPT_VERSION, result.promptVersion()),
                server::verify
        );
    }

    @Test
    @DisplayName("꼬리 질문 답변 분석의 SKIP 결과를 변환한다")
    void mapsSkipResultForFollowUpAnswer() throws JacksonException {
        server.expect(once(), requestTo(BASE_URL + "/v1/responses"))
                .andRespond(withSuccess(
                        completedResponse(analysisPayload("SKIP", null)),
                        MediaType.APPLICATION_JSON
                ));

        AnswerAnalysisResult result = provider.analyze(followUpAnswerPrompt());

        assertAll(
                () -> assertSame(FollowUpDecision.SKIP, result.followUpDecision()),
                () -> assertNull(result.followUpQuestion()),
                server::verify
        );
    }

    @Test
    @DisplayName("불완전한 응답을 유효하지 않은 응답으로 변환한다")
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

        assertFailure(AnswerAnalysisFailure.INVALID_RESPONSE, coreAnswerPrompt());
    }

    @Test
    @DisplayName("모델의 거절 응답을 유효하지 않은 응답으로 변환한다")
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

        assertFailure(AnswerAnalysisFailure.INVALID_RESPONSE, coreAnswerPrompt());
    }

    @Test
    @DisplayName("분석 결과 JSON이 잘못되면 유효하지 않은 응답으로 변환한다")
    void rejectsMalformedAnalysisPayload() throws JacksonException {
        server.expect(once(), requestTo(BASE_URL + "/v1/responses"))
                .andRespond(withSuccess(
                        completedResponse("not-json"),
                        MediaType.APPLICATION_JSON
                ));

        assertFailure(AnswerAnalysisFailure.INVALID_RESPONSE, coreAnswerPrompt());
    }

    @Test
    @DisplayName("도메인 제약을 위반한 분석 결과를 거부한다")
    void rejectsAnalysisPayloadViolatingDomainConstraint() throws JacksonException {
        server.expect(once(), requestTo(BASE_URL + "/v1/responses"))
                .andRespond(withSuccess(
                        completedResponse(analysisPayload("ASK", null)),
                        MediaType.APPLICATION_JSON
                ));

        assertFailure(AnswerAnalysisFailure.INVALID_RESPONSE, coreAnswerPrompt());
    }

    @ParameterizedTest
    @MethodSource("httpFailureCases")
    @DisplayName("OpenAI HTTP 오류를 답변 분석 실패 유형으로 변환한다")
    void mapsHttpStatusToAnalysisFailure(
            int status,
            AnswerAnalysisFailure expectedFailure
    ) {
        server.expect(once(), requestTo(BASE_URL + "/v1/responses"))
                .andRespond(withRawStatus(status));

        assertFailure(expectedFailure, coreAnswerPrompt());
    }

    private void assertFailure(
            AnswerAnalysisFailure expectedFailure,
            AnswerAnalysisPrompt prompt
    ) {
        AnswerAnalysisException exception = assertThrows(
                AnswerAnalysisException.class,
                () -> provider.analyze(prompt)
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

    private Map<String, Object> analysisPayload(
            String followUpDecision,
            String followUpQuestion
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "핵심 개념을 설명했지만 일부 보완이 필요합니다.");
        payload.put("strengths", List.of("경쟁 상태를 언급했습니다."));
        payload.put("improvements", List.of("가시성 설명이 필요합니다."));
        payload.put("followUpDecision", followUpDecision);
        payload.put("followUpReason", "가시성 이해를 확인해야 합니다.");
        payload.put("followUpQuestion", followUpQuestion);
        return payload;
    }

    private AnswerAnalysisPrompt coreAnswerPrompt() {
        return new AnswerAnalysisPrompt(
                "JAVA",
                "여러 스레드에서 공유 상태를 변경할 때 발생하는 문제를 설명해 주세요.",
                null,
                "동시성 문제로 값이 예상과 다르게 변경될 수 있습니다.",
                true
        );
    }

    private AnswerAnalysisPrompt followUpAnswerPrompt() {
        return new AnswerAnalysisPrompt(
                "JAVA",
                "여러 스레드에서 공유 상태를 변경할 때 발생하는 문제를 설명해 주세요.",
                "volatile은 무엇을 보장하나요?",
                "volatile은 가시성을 보장합니다.",
                false
        );
    }

    private static Stream<Arguments> httpFailureCases() {
        return Stream.of(
                Arguments.of(401, AnswerAnalysisFailure.AUTHENTICATION_FAILED),
                Arguments.of(429, AnswerAnalysisFailure.RATE_LIMITED),
                Arguments.of(500, AnswerAnalysisFailure.TEMPORARY_FAILURE)
        );
    }
}
