package com.ward.techview;

import com.jayway.jsonpath.JsonPath;
import com.ward.techview.account.auth.repository.RefreshTokenRepository;
import com.ward.techview.account.member.repository.MemberRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountFlowIntegrationTests {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "password123!";
    private static final String NICKNAME = "사용자";
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
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("회원가입부터 로그인과 보호된 프로필 조회까지 성공한다")
    void shouldCompleteSignUpLoginAndProtectedProfileFlow() throws Exception {
        signUp(EMAIL, PASSWORD, NICKNAME)
                .andExpect(status().isCreated());

        LoginSession session = login(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.nickname").value(NICKNAME));

        assertEquals(1, memberRepository.count());
        assertEquals(1, refreshTokenRepository.count());
    }

    @Test
    @DisplayName("중복 이메일 회원가입과 잘못된 비밀번호 로그인을 거부한다")
    void shouldRejectDuplicateEmailAndIncorrectPassword() throws Exception {
        signUp(EMAIL, PASSWORD, NICKNAME)
                .andExpect(status().isCreated());

        signUp(EMAIL, PASSWORD, "다른사용자")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(2000));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(2001));

        assertEquals(1, memberRepository.count());
        assertEquals(0, refreshTokenRepository.count());
    }

    @Test
    @DisplayName("Access Token이 없거나 만료 또는 변조되면 보호 API 접근을 거부한다")
    void shouldRejectMissingExpiredAndTamperedAccessTokens() throws Exception {
        signUp(EMAIL, PASSWORD, NICKNAME)
                .andExpect(status().isCreated());
        LoginSession session = login(EMAIL, PASSWORD);

        assertAuthenticationRequired(null);
        assertAuthenticationRequired(expiredAccessToken(session.memberId()));
        assertAuthenticationRequired(tamper(session.accessToken()));
    }

    @Test
    @DisplayName("Refresh Token 재발급 시 기존 토큰을 폐기하고 새 토큰만 사용할 수 있다")
    void shouldRotateRefreshTokenAndRejectPreviousToken() throws Exception {
        signUp(EMAIL, PASSWORD, NICKNAME)
                .andExpect(status().isCreated());
        LoginSession session = login(EMAIL, PASSWORD);

        MvcResult reissueResult = refresh(session.refreshToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn();
        String rotatedRefreshToken = refreshTokenFrom(reissueResult);

        assertNotEquals(session.refreshToken(), rotatedRefreshToken);
        assertFalse(refreshTokenRepository.findByToken(session.refreshToken()).isPresent());
        assertTrue(refreshTokenRepository.findByToken(rotatedRefreshToken).isPresent());

        refresh(session.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));

        refresh(rotatedRefreshToken)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그아웃한 Refresh Token은 재발급에 사용할 수 없다")
    void shouldRejectRefreshTokenAfterLogout() throws Exception {
        signUp(EMAIL, PASSWORD, NICKNAME)
                .andExpect(status().isCreated());
        LoginSession session = login(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(refreshTokenCookie(session.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        allOf(
                                containsString("refreshToken="),
                                containsString("Max-Age=0")
                        )
                ));

        refresh(session.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    @Timeout(20)
    @DisplayName("같은 Refresh Token의 동시 재발급 요청은 하나만 성공한다")
    void shouldAllowOnlyOneConcurrentRefreshRequest() throws Exception {
        signUp(EMAIL, PASSWORD, NICKNAME)
                .andExpect(status().isCreated());
        LoginSession session = login(EMAIL, PASSWORD);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<MvcResult> refreshRequest = () -> {
            ready.countDown();
            start.await();
            return mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(refreshTokenCookie(session.refreshToken())))
                    .andReturn();
        };

        try {
            Future<MvcResult> first = executor.submit(refreshRequest);
            Future<MvcResult> second = executor.submit(refreshRequest);
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

            assertEquals(List.of(200, 401), statuses);

            MvcResult success = results.stream()
                    .filter(result -> result.getResponse().getStatus() == 200)
                    .findFirst()
                    .orElseThrow();
            String rotatedRefreshToken = refreshTokenFrom(success);

            refresh(rotatedRefreshToken)
                    .andExpect(status().isOk());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("회원 탈퇴 시 회원과 Refresh Token을 삭제하고 쿠키를 만료한다")
    void shouldDeleteMemberAndCascadeRefreshTokensWhenWithdrawing() throws Exception {
        signUp(EMAIL, PASSWORD, NICKNAME)
                .andExpect(status().isCreated());
        LoginSession session = login(EMAIL, PASSWORD);

        mockMvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        allOf(
                                containsString("refreshToken="),
                                containsString("Max-Age=0")
                        )
                ));

        assertEquals(0, memberRepository.count());
        assertEquals(0, refreshTokenRepository.count());

        mockMvc.perform(get("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2002));
    }

    private org.springframework.test.web.servlet.ResultActions signUp(
            String email,
            String password,
            String nickname
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signUpBody(email, password, nickname)));
    }

    private LoginSession login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String accessToken = JsonPath.read(responseBody, "$.data.accessToken");
        Number memberId = JsonPath.read(responseBody, "$.data.memberId");

        return new LoginSession(
                accessToken,
                refreshTokenFrom(result),
                memberId.longValue()
        );
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(refreshTokenCookie(refreshToken)));
    }

    private void assertAuthenticationRequired(String accessToken) throws Exception {
        var request = get("/api/v1/members/me");
        if (accessToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer(accessToken));
        }

        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    private static Cookie refreshTokenCookie(String token) {
        return new Cookie("refreshToken", token);
    }

    private static String refreshTokenFrom(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        if (setCookie == null) {
            throw new IllegalStateException("Refresh Token 쿠키가 응답에 없습니다.");
        }

        String cookiePair = setCookie.split(";", 2)[0];
        return cookiePair.substring("refreshToken=".length());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String tamper(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char original = token.charAt(signatureStart);
        char replacement = original == 'A' ? 'B' : 'A';
        return token.substring(0, signatureStart)
                + replacement
                + token.substring(signatureStart + 1);
    }

    private static String expiredAccessToken(Long memberId) {
        Instant now = Instant.now();
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .id("expired-access-token")
                .issuer(JWT_ISSUER)
                .subject(EMAIL)
                .claim("memberId", memberId)
                .claim("tokenType", "access")
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private static String signUpBody(String email, String password, String nickname) {
        return """
                {
                  "email": "%s",
                  "password": "%s",
                  "nickname": "%s"
                }
                """.formatted(email, password, nickname);
    }

    private static String loginBody(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }

    private record LoginSession(
            String accessToken,
            String refreshToken,
            Long memberId
    ) {
    }
}
