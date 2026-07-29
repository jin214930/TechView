package com.ward.techview.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {
    private static final int MINIMUM_SECRET_KEY_BYTES = 32;
    private static final String MEMBER_ID = "memberId";
    private static final String TOKEN_TYPE = "tokenType";
    private static final String ACCESS_TOKEN = "access";
    private static final String REFRESH_TOKEN = "refresh";

    private final String issuer;
    private final SecretKey secretKey;
    private final Duration accessTokenExpirationTime;
    private final Duration refreshTokenExpirationTime;

    public JwtProvider(
            @Value("${custom.jwt.issuer}") String issuer,
            @Value("${custom.jwt.secret-key}") String secretKey,
            @Value("${custom.jwt.access-token.expiration-time}") Duration accessTokenExpirationTime,
            @Value("${custom.jwt.refresh-token.expiration-time}") Duration refreshTokenExpirationTime
    ) {
        validateText(issuer, "JWT issuer는 비어 있을 수 없습니다.");
        validateText(secretKey, "JWT secret key는 비어 있을 수 없습니다.");
        validateExpirationTime(accessTokenExpirationTime, "Access Token 만료 시간은 0보다 커야 합니다.");
        validateExpirationTime(refreshTokenExpirationTime, "Refresh Token 만료 시간은 0보다 커야 합니다.");

        byte[] secretKeyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (secretKeyBytes.length < MINIMUM_SECRET_KEY_BYTES) {
            throw new IllegalArgumentException("JWT secret key는 UTF-8 기준 32바이트 이상이어야 합니다.");
        }

        this.issuer = issuer;
        this.secretKey = Keys.hmacShaKeyFor(secretKeyBytes);
        this.accessTokenExpirationTime = accessTokenExpirationTime;
        this.refreshTokenExpirationTime = refreshTokenExpirationTime;
    }

    public IssuedToken generateAccessToken(Long memberId, String email) {
        return generateToken(memberId, email, ACCESS_TOKEN, accessTokenExpirationTime);
    }

    public IssuedToken generateRefreshToken(Long memberId, String email) {
        return generateToken(memberId, email, REFRESH_TOKEN, refreshTokenExpirationTime);
    }

    public CustomUserDetails getAccessTokenPrincipal(String token) {
        return getTokenPrincipal(token, ACCESS_TOKEN);
    }

    public CustomUserDetails getRefreshTokenPrincipal(String token) {
        return getTokenPrincipal(token, REFRESH_TOKEN);
    }

    private IssuedToken generateToken(
            Long memberId,
            String email,
            String tokenType,
            Duration expirationTime
    ) {
        if (memberId == null) {
            throw new IllegalArgumentException("회원 ID는 비어 있을 수 없습니다.");
        }
        validateText(email, "이메일은 비어 있을 수 없습니다.");

        Instant now = Instant.now();
        Instant expiration = now.plus(expirationTime);

        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(email)
                .claim(MEMBER_ID, memberId)
                .claim(TOKEN_TYPE, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();

        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(expiration, ZoneOffset.UTC);
        return new IssuedToken(token, expiresAt);
    }

    private CustomUserDetails getTokenPrincipal(String token, String expectedTokenType) {
        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            Claims claims = extractClaims(token);

            if (!expectedTokenType.equals(claims.get(TOKEN_TYPE, String.class))) {
                return null;
            }

            Object memberIdClaim = claims.get(MEMBER_ID);
            if (!(memberIdClaim instanceof Number memberId)) {
                return null;
            }

            String email = claims.getSubject();
            if (email == null || email.isBlank()) {
                return null;
            }

            return new CustomUserDetails(memberId.longValue(), email);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static void validateText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void validateExpirationTime(Duration expirationTime, String message) {
        if (expirationTime == null || expirationTime.isZero() || expirationTime.isNegative()) {
            throw new IllegalArgumentException(message);
        }
    }
}
