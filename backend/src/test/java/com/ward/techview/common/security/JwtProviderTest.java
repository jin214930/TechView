package com.ward.techview.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtProviderTest {

    private static final String ISSUER = "techView";
    private static final String SECRET = "TECHVIEW_JWT_SECRET_KEY_NEED_TO_CHANGE";
    private static final String OTHER_SECRET = "ANOTHER_JWT_SECRET_KEY_NEED_TO_CHANGE";
    private static final Duration ACCESS_EXPIRATION = Duration.ofMinutes(15);
    private static final Duration REFRESH_EXPIRATION = Duration.ofDays(30);
    private static final Long MEMBER_ID = 1L;
    private static final String EMAIL = "user@example.com";

    private final SecretKey secretKey = key(SECRET);
    private final JwtProvider jwtProvider = provider();

    @Test
    @DisplayName("Access Token은 HS256과 필수 Claims 및 설정된 만료 시간을 포함한다")
    void shouldGenerateAccessTokenWithExpectedHeaderAndClaims() {
        IssuedToken issuedToken = jwtProvider.generateAccessToken(MEMBER_ID, EMAIL);

        Jws<Claims> parsedToken = parse(issuedToken.value(), ISSUER, secretKey);
        Claims claims = parsedToken.getPayload();

        assertAll(
                () -> assertEquals("HS256", parsedToken.getHeader().getAlgorithm()),
                () -> assertEquals(ISSUER, claims.getIssuer()),
                () -> assertEquals(EMAIL, claims.getSubject()),
                () -> assertEquals(MEMBER_ID, ((Number) claims.get("memberId")).longValue()),
                () -> assertEquals("access", claims.get("tokenType", String.class)),
                () -> assertNotNull(claims.getIssuedAt()),
                () -> assertNotNull(claims.getExpiration()),
                () -> assertEquals(
                        ACCESS_EXPIRATION,
                        Duration.between(
                                claims.getIssuedAt().toInstant(),
                                claims.getExpiration().toInstant()
                        )
                ),
                () -> assertEquals(
                        claims.getExpiration().toInstant().getEpochSecond(),
                        issuedToken.expiresAt().toInstant().getEpochSecond()
                )
        );
    }

    @Test
    @DisplayName("Access Token을 파싱하면 회원 principal을 반환한다")
    void shouldReturnPrincipalFromAccessToken() {
        String token = jwtProvider.generateAccessToken(MEMBER_ID, EMAIL).value();

        CustomUserDetails principal = jwtProvider.getAccessTokenPrincipal(token);

        assertNotNull(principal);
        assertEquals(MEMBER_ID, principal.id());
        assertEquals(EMAIL, principal.email());
    }

    @Test
    @DisplayName("Refresh Token을 파싱하면 회원 principal을 반환한다")
    void shouldReturnPrincipalFromRefreshToken() {
        String token = jwtProvider.generateRefreshToken(MEMBER_ID, EMAIL).value();

        CustomUserDetails principal = jwtProvider.getRefreshTokenPrincipal(token);

        assertNotNull(principal);
        assertEquals(MEMBER_ID, principal.id());
        assertEquals(EMAIL, principal.email());
    }

    @Test
    @DisplayName("Access Token과 Refresh Token은 반대 타입 principal로 사용할 수 없다")
    void shouldRejectTokenWhenTokenTypeDoesNotMatch() {
        String accessToken = jwtProvider.generateAccessToken(MEMBER_ID, EMAIL).value();
        String refreshToken = jwtProvider.generateRefreshToken(MEMBER_ID, EMAIL).value();

        assertNull(jwtProvider.getRefreshTokenPrincipal(accessToken));
        assertNull(jwtProvider.getAccessTokenPrincipal(refreshToken));
    }

    @Test
    @DisplayName("만료된 토큰은 거부한다")
    void shouldRejectExpiredToken() {
        Instant now = Instant.now();
        String token = createToken(
                ISSUER,
                secretKey,
                EMAIL,
                MEMBER_ID,
                "access",
                now.minusSeconds(120),
                now.minusSeconds(60)
        );

        assertNull(jwtProvider.getAccessTokenPrincipal(token));
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 거부한다")
    void shouldRejectTokenSignedWithDifferentKey() {
        Instant now = Instant.now();
        String token = createToken(
                ISSUER,
                key(OTHER_SECRET),
                EMAIL,
                MEMBER_ID,
                "access",
                now,
                now.plusSeconds(60)
        );

        assertNull(jwtProvider.getAccessTokenPrincipal(token));
    }

    @Test
    @DisplayName("issuer가 다른 토큰은 거부한다")
    void shouldRejectTokenWithDifferentIssuer() {
        Instant now = Instant.now();
        String token = createToken(
                "other-service",
                secretKey,
                EMAIL,
                MEMBER_ID,
                "access",
                now,
                now.plusSeconds(60)
        );

        assertNull(jwtProvider.getAccessTokenPrincipal(token));
    }

    @Test
    @DisplayName("필수 Claim이 누락된 토큰은 거부한다")
    void shouldRejectTokenWithMissingRequiredClaim() {
        Instant now = Instant.now();

        String withoutTokenType = createToken(
                ISSUER, secretKey, EMAIL, MEMBER_ID, null, now, now.plusSeconds(60)
        );
        String withoutMemberId = createToken(
                ISSUER, secretKey, EMAIL, null, "access", now, now.plusSeconds(60)
        );
        String withoutSubject = createToken(
                ISSUER, secretKey, null, MEMBER_ID, "access", now, now.plusSeconds(60)
        );

        assertAll(
                () -> assertNull(jwtProvider.getAccessTokenPrincipal(withoutTokenType)),
                () -> assertNull(jwtProvider.getAccessTokenPrincipal(withoutMemberId)),
                () -> assertNull(jwtProvider.getAccessTokenPrincipal(withoutSubject))
        );
    }

    @Test
    @DisplayName("토큰이 null이거나 비어 있으면 principal을 반환하지 않는다")
    void shouldRejectNullOrBlankToken() {
        assertAll(
                () -> assertNull(jwtProvider.getAccessTokenPrincipal(null)),
                () -> assertNull(jwtProvider.getAccessTokenPrincipal("")),
                () -> assertNull(jwtProvider.getAccessTokenPrincipal("   "))
        );
    }

    @Test
    @DisplayName("회원 ID나 이메일이 유효하지 않으면 토큰을 생성할 수 없다")
    void shouldRejectInvalidTokenGenerationInput() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> jwtProvider.generateAccessToken(null, EMAIL)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> jwtProvider.generateAccessToken(MEMBER_ID, null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> jwtProvider.generateAccessToken(MEMBER_ID, "   ")
                )
        );
    }

    @Test
    @DisplayName("issuer나 비밀키가 비어 있으면 Provider를 생성할 수 없다")
    void shouldRejectBlankIssuerOrSecret() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new JwtProvider(" ", SECRET, ACCESS_EXPIRATION, REFRESH_EXPIRATION)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new JwtProvider(ISSUER, " ", ACCESS_EXPIRATION, REFRESH_EXPIRATION)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new JwtProvider(ISSUER, "short-secret", ACCESS_EXPIRATION, REFRESH_EXPIRATION)
                )
        );
    }

    @Test
    @DisplayName("만료 시간이 null이거나 0 이하이면 Provider를 생성할 수 없다")
    void shouldRejectInvalidExpirationTime() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new JwtProvider(ISSUER, SECRET, null, REFRESH_EXPIRATION)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new JwtProvider(ISSUER, SECRET, Duration.ZERO, REFRESH_EXPIRATION)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new JwtProvider(ISSUER, SECRET, ACCESS_EXPIRATION, Duration.ofSeconds(-1))
                )
        );
    }

    private static JwtProvider provider() {
        return new JwtProvider(ISSUER, SECRET, ACCESS_EXPIRATION, REFRESH_EXPIRATION);
    }

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static Jws<Claims> parse(String token, String issuer, SecretKey key) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);
    }

    private static String createToken(
            String issuer,
            SecretKey key,
            String subject,
            Long memberId,
            String tokenType,
            Instant issuedAt,
            Instant expiration
    ) {
        var builder = Jwts.builder()
                .issuer(issuer)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration));

        if (subject != null) {
            builder.subject(subject);
        }
        if (memberId != null) {
            builder.claim("memberId", memberId);
        }
        if (tokenType != null) {
            builder.claim("tokenType", tokenType);
        }

        return builder
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
