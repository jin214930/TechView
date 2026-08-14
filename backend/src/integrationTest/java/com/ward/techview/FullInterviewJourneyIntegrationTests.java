package com.ward.techview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ward.techview.evaluation.api.analysis.*;
import com.ward.techview.evaluation.api.feedback.*;
import com.ward.techview.voice.api.storage.AudioObject;
import com.ward.techview.voice.api.stt.SpeechToTextProvider;
import com.ward.techview.voice.api.stt.TranscriptionResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(FullInterviewJourneyIntegrationTests.ProviderStubConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FullInterviewJourneyIntegrationTests {

    private static final String BUCKET = "techview-journey-audio";
    private static final String ACCESS_KEY = "techview-test-access";
    private static final String SECRET_KEY = "techview-test-secret-key";
    private static final byte[] AUDIO = "deterministic-test-audio".getBytes();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static final GenericContainer<?> minio = new GenericContainer<>(
            DockerImageName.parse("minio/minio:RELEASE.2025-04-22T22-12-26Z")
    )
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    static {
        minio.start();
    }

    @DynamicPropertySource
    static void configureStorage(DynamicPropertyRegistry registry) {
        registry.add("storage.audio.endpoint", FullInterviewJourneyIntegrationTests::minioEndpoint);
        registry.add("storage.audio.bucket", () -> BUCKET);
        registry.add("storage.audio.access-key", () -> ACCESS_KEY);
        registry.add("storage.audio.secret-key", () -> SECRET_KEY);
        registry.add("storage.audio.path-style-access-enabled", () -> true);
    }

    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    S3Client s3Client;

    @BeforeAll
    void createBucket() {
        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }

    @Test
    @DisplayName("회원가입부터 면접 완료와 피드백 조회까지 전체 흐름이 동작한다")
    void completesFullInterviewJourney() throws Exception {
        String accessToken = signUpAndLogin();
        JsonNode start = responseData(post("/api/v1/interviews"), accessToken, null, 201);
        long sessionId = start.path("sessionId").asLong();
        long turnId = start.path("turnId").asLong();
        MvcResult eventStream = subscribe(accessToken, sessionId);
        int coreTurnCount = 0;
        int followUpTurnCount = 0;

        while (true) {
            String turnType = start.path("turnType").asText();
            if ("CORE".equals(turnType)) {
                coreTurnCount++;
            } else {
                followUpTurnCount++;
            }

            submitAnswer(accessToken, sessionId, turnId);
            JsonNode progress = awaitProgress(accessToken, sessionId);

            if ("COMPLETED".equals(progress.path("status").asText())) {
                break;
            }

            assertThat(progress.path("status").asText()).isEqualTo("TURN_READY");
            start = progress;
            turnId = progress.path("turnId").asLong();
        }

        assertThat(coreTurnCount).isEqualTo(5);
        assertThat(followUpTurnCount).isEqualTo(1);

        JsonNode feedback = awaitFeedback(accessToken, sessionId);
        assertThat(feedback.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(feedback.path("feedback").path("categoryFeedbacks").size()).isEqualTo(5);

        JsonNode history = responseData(get("/api/v1/interviews?page=0&size=10"), accessToken, null, 200);
        assertThat(history.path("items").size()).isEqualTo(1);
        assertThat(history.path("items").get(0).path("sessionId").asLong()).isEqualTo(sessionId);
        assertThat(history.path("items").get(0).path("feedbackStatus").asText()).isEqualTo("COMPLETED");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            String events = eventStream.getResponse().getContentAsString();
            assertThat(events).contains("event:connected");
            assertThat(events).contains("event:progress-changed");
            assertThat(events).contains("event:feedback-status-changed");
        });

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(BUCKET).build()).contents())
                        .isEmpty()
        );
    }

    private String signUpAndLogin() throws Exception {
        String email = "journey@example.com";
        String password = "password123!";
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","nickname":"journey"}
                                """.formatted(email, password)))
                .andExpect(status().isCreated());

        JsonNode login = responseData(post("/api/v1/auth/login"), null,
                """
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password), 200);
        return login.path("accessToken").asText();
    }

    private void submitAnswer(String token, long sessionId, long turnId) throws Exception {
        JsonNode answer = responseData(
                post("/api/v1/interviews/{sessionId}/turns/{turnId}/answers", sessionId, turnId),
                token,
                "{\"audioContentType\":\"audio/webm\",\"audioDurationMs\":1000}",
                201
        );

        long answerId = answer.path("answerId").asLong();
        putAudio(answer.path("uploadUrl").asText());
        responseData(post("/api/v1/answers/{answerId}/upload-completion", answerId), token, null, 202);
    }

    private MvcResult subscribe(String token, long sessionId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/interviews/{sessionId}/events", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getRequest().isAsyncStarted()).isTrue();
        return result;
    }

    private void putAudio(String uploadUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(uploadUrl))
                .header(HttpHeaders.CONTENT_TYPE, "audio/webm")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(AUDIO))
                .build();
        HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isBetween(200, 299);
    }

    private JsonNode awaitProgress(String token, long sessionId) {
        AtomicReferenceHolder result = new AtomicReferenceHolder();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            JsonNode progress = responseData(get("/api/v1/interviews/{sessionId}/progress", sessionId), token, null, 200);
            assertThat(progress.path("status").asText()).isIn("TURN_READY", "COMPLETED", "FAILED");
            assertThat(progress.path("status").asText()).isNotEqualTo("FAILED");
            result.value = progress;
        });
        return result.value;
    }

    private JsonNode awaitFeedback(String token, long sessionId) {
        AtomicReferenceHolder result = new AtomicReferenceHolder();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            JsonNode feedback = responseData(get("/api/v1/interviews/{sessionId}/feedback", sessionId), token, null, 200);
            assertThat(feedback.path("status").asText()).isEqualTo("COMPLETED");
            result.value = feedback;
        });
        return result.value;
    }

    private JsonNode responseData(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String token,
            String body,
            int expectedStatus
    ) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    private static String minioEndpoint() {
        return "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
    }

    private static final class AtomicReferenceHolder {
        private JsonNode value;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProviderStubConfiguration {
        @Bean
        @Primary
        SpeechToTextProvider deterministicSpeechToTextProvider() {
            return audio -> new TranscriptionResult("테스트 답변입니다.");
        }

        @Bean
        @Primary
        AnswerAnalysisProvider deterministicAnswerAnalysisProvider() {
            AtomicInteger calls = new AtomicInteger();
            return prompt -> {
                boolean ask = calls.getAndIncrement() == 0 && prompt.followUpAllowed();
                return new AnswerAnalysisResult(
                        "답변 분석 요약",
                        List.of("핵심 개념을 설명했습니다."),
                        List.of("구체적인 예시를 보강하세요."),
                        ask ? FollowUpDecision.ASK : FollowUpDecision.SKIP,
                        ask ? "첫 답변의 이해도를 더 확인합니다." : "다음 핵심 질문으로 진행합니다.",
                        ask ? "실제 적용 사례를 하나 설명해 주세요." : null,
                        "deterministic-analysis",
                        "test-v1"
                );
            };
        }

        @Bean
        @Primary
        InterviewFeedbackProvider deterministicInterviewFeedbackProvider() {
            return prompt -> new InterviewFeedbackResult(
                    "전체적으로 핵심 개념을 잘 설명했습니다.",
                    List.of("답변 구조가 명확합니다."),
                    List.of("사례를 더 구체화하세요."),
                    List.of("JAVA", "SPRING", "DATABASE", "OS", "NETWORK").stream()
                            .map(category -> new CategoryFeedback(category, "기본기를 확인했습니다.", "관련 사례를 복습하세요."))
                            .toList(),
                    List.of("동시성", "데이터베이스 인덱스"),
                    "deterministic-feedback",
                    "test-v1"
            );
        }
    }
}
