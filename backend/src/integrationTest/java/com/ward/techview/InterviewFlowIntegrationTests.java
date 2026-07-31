package com.ward.techview;

import com.jayway.jsonpath.JsonPath;
import com.ward.techview.account.member.entity.Member;
import com.ward.techview.account.member.repository.MemberRepository;
import com.ward.techview.interview.entity.InterviewCoreQuestion;
import com.ward.techview.interview.repository.AnswerRepository;
import com.ward.techview.interview.repository.InterviewCoreQuestionRepository;
import com.ward.techview.interview.repository.InterviewSessionRepository;
import com.ward.techview.interview.repository.InterviewTurnRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InterviewFlowIntegrationTests {

    private static final String EMAIL = "interview-user@example.com";
    private static final String JWT_ISSUER = "techView-test";
    private static final String JWT_SECRET = "TECHVIEW_JWT_SECRET_KEY_FOR_INTEGRATION_TESTS";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private InterviewCoreQuestionRepository interviewCoreQuestionRepository;

    @Autowired
    private InterviewTurnRepository interviewTurnRepository;

    @Autowired
    private AnswerRepository answerRepository;

    private Long memberId;
    private String accessToken;

    @BeforeEach
    void setUp() {
        answerRepository.deleteAllInBatch();
        interviewTurnRepository.deleteAllInBatch();
        interviewCoreQuestionRepository.deleteAllInBatch();
        interviewSessionRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Member member = memberRepository.saveAndFlush(
                Member.create(EMAIL, "encoded-password", "면접사용자")
        );
        memberId = member.getId();
        accessToken = accessToken(memberId);
    }

    @Test
    @DisplayName("현재 면접 턴에 답변을 생성하고 Presigned 업로드 URL을 반환한다")
    void shouldCreateAnswerAndReturnPresignedUploadUrl() throws Exception {
        MvcResult started = mockMvc.perform(post("/api/v1/interviews")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isCreated())
                .andReturn();
        String startedBody = started.getResponse().getContentAsString();
        long sessionId = JsonPath.<Number>read(startedBody, "$.data.sessionId").longValue();
        long turnId = JsonPath.<Number>read(startedBody, "$.data.turnId").longValue();

        MvcResult createdAnswer = mockMvc.perform(post(
                        "/api/v1/interviews/{sessionId}/turns/{turnId}/answers",
                        sessionId,
                        turnId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.answerId").isNumber())
                .andExpect(jsonPath("$.data.uploadUrl").value(
                        org.hamcrest.Matchers.startsWith("http://localhost:9000/techview-test-audio/")
                ))
                .andExpect(jsonPath("$.data.uploadExpiresAt").isNotEmpty())
                .andReturn();

        long answerId = JsonPath.<Number>read(
                createdAnswer.getResponse().getContentAsString(),
                "$.data.answerId"
        ).longValue();

        assertEquals(1, answerRepository.count());

        mockMvc.perform(post(
                        "/api/v1/interviews/{sessionId}/turns/{turnId}/answers",
                        sessionId,
                        turnId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.created").value(false))
                .andExpect(jsonPath("$.data.answerId").value(answerId))
                .andExpect(jsonPath("$.data.uploadUrl").value(
                        org.hamcrest.Matchers.startsWith("http://localhost:9000/techview-test-audio/")
                ))
                .andExpect(jsonPath("$.data.uploadExpiresAt").isNotEmpty());

        assertEquals(1, answerRepository.count());
    }

    @Test
    @Timeout(20)
    @DisplayName("동일한 면접 턴의 동시 요청은 답변 하나를 생성하고 같은 답변을 반환한다")
    void shouldCreateOneAnswerAndReturnItForConcurrentRequests() throws Exception {
        MvcResult started = mockMvc.perform(post("/api/v1/interviews")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isCreated())
                .andReturn();
        String startedBody = started.getResponse().getContentAsString();
        long sessionId = JsonPath.<Number>read(startedBody, "$.data.sessionId").longValue();
        long turnId = JsonPath.<Number>read(startedBody, "$.data.turnId").longValue();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<MvcResult> answerRequest = () -> {
            ready.countDown();
            start.await();
            return mockMvc.perform(post(
                            "/api/v1/interviews/{sessionId}/turns/{turnId}/answers",
                            sessionId,
                            turnId
                    )
                            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(answerRequest()))
                    .andReturn();
        };

        try {
            Future<MvcResult> first = executor.submit(answerRequest);
            Future<MvcResult> second = executor.submit(answerRequest);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<MvcResult> results = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );
            List<Integer> statuses = results.stream()
                    .map(result -> result.getResponse().getStatus())
                    .sorted()
                    .toList();

            assertEquals(List.of(200, 201), statuses);
            assertEquals(1, answerRepository.count());

            List<Long> answerIds = results.stream()
                    .map(result -> JsonPath.<Number>read(
                            new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                            "$.data.answerId"
                    ).longValue())
                    .distinct()
                    .toList();
            assertEquals(1, answerIds.size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("면접을 처음 시작하면 질문 5개와 첫 턴을 생성하고 다음 요청에서 재개한다")
    void shouldCreateInterviewAndResumeItOnNextRequest() throws Exception {
        mockMvc.perform(get("/api/v1/interviews/active")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasActiveSession").value(false));

        MvcResult created = mockMvc.perform(post("/api/v1/interviews")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.resumed").value(false))
                .andExpect(jsonPath("$.data.turnOrder").value(1))
                .andExpect(jsonPath("$.data.turnType").value("CORE"))
                .andExpect(jsonPath("$.data.question").isNotEmpty())
                .andReturn();

        Number createdSessionId = JsonPath.read(
                created.getResponse().getContentAsString(),
                "$.data.sessionId"
        );

        mockMvc.perform(get("/api/v1/interviews/active")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasActiveSession").value(true))
                .andExpect(jsonPath("$.data.sessionId").value(createdSessionId.longValue()))
                .andExpect(jsonPath("$.data.currentTurnOrder").value(1));

        mockMvc.perform(post("/api/v1/interviews")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumed").value(true))
                .andExpect(jsonPath("$.data.sessionId").value(createdSessionId.longValue()))
                .andExpect(jsonPath("$.data.turnOrder").value(1));

        assertEquals(1, interviewSessionRepository.count());
        assertEquals(5, interviewCoreQuestionRepository.count());
        assertEquals(1, interviewTurnRepository.count());

        List<InterviewCoreQuestion> coreQuestions = interviewCoreQuestionRepository.findAll();
        assertEquals(
                List.of(1, 2, 3, 4, 5),
                coreQuestions.stream()
                        .map(InterviewCoreQuestion::getQuestionOrder)
                        .sorted()
                        .toList()
        );
        assertEquals(
                5,
                coreQuestions.stream()
                        .map(InterviewCoreQuestion::getCategory)
                        .distinct()
                        .count()
        );
    }

    @Test
    @Timeout(20)
    @DisplayName("동일 회원의 동시 면접 시작 요청은 세션 하나만 생성한다")
    void shouldCreateOnlyOneSessionForConcurrentStartRequests() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<MvcResult> startRequest = () -> {
            ready.countDown();
            start.await();
            return mockMvc.perform(post("/api/v1/interviews")
                            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                    .andReturn();
        };

        try {
            Future<MvcResult> first = executor.submit(startRequest);
            Future<MvcResult> second = executor.submit(startRequest);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<MvcResult> results = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );
            List<Integer> statuses = results.stream()
                    .map(result -> result.getResponse().getStatus())
                    .sorted()
                    .toList();

            assertEquals(List.of(200, 201), statuses);
            assertEquals(1, interviewSessionRepository.count());
            assertEquals(5, interviewCoreQuestionRepository.count());
            assertEquals(1, interviewTurnRepository.count());

            List<Boolean> resumedValues = results.stream()
                    .map(result -> JsonPath.<Boolean>read(
                            new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                            "$.data.resumed"
                    ))
                    .toList();
            assertTrue(resumedValues.contains(true));
            assertTrue(resumedValues.contains(false));
            assertFalse(resumedValues.get(0).equals(resumedValues.get(1)));
        } finally {
            executor.shutdownNow();
        }
    }

    private static String accessToken(Long memberId) {
        Instant now = Instant.now();
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(JWT_ISSUER)
                .subject(EMAIL)
                .claim("memberId", memberId)
                .claim("tokenType", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String answerRequest() {
        return """
                {
                  "audioContentType": "audio/webm",
                  "audioDurationMs": 42000
                }
                """;
    }
}
