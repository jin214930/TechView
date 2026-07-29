package com.ward.techview.account.auth.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenCookieManagerTest {

    private static final Duration REFRESH_TOKEN_EXPIRATION = Duration.ofDays(30);

    private TokenCookieManager tokenCookieManager;

    @BeforeEach
    void setUp() {
        tokenCookieManager = new TokenCookieManager();
        ReflectionTestUtils.setField(
                tokenCookieManager,
                "refreshTokenExpirationTime",
                REFRESH_TOKEN_EXPIRATION
        );
        ReflectionTestUtils.setField(tokenCookieManager, "httpOnly", true);
        ReflectionTestUtils.setField(tokenCookieManager, "path", "/api/v1/auth");
        ReflectionTestUtils.setField(tokenCookieManager, "secure", false);
        ReflectionTestUtils.setField(tokenCookieManager, "sameSite", "lax");
    }

    @Test
    @DisplayName("Refresh Token 쿠키에 보안 속성과 만료 시간을 설정한다")
    void shouldCreateRefreshTokenCookieWithConfiguredAttributes() {
        ResponseCookie cookie = tokenCookieManager.create("refresh-token");

        assertAll(
                () -> assertEquals("refreshToken", cookie.getName()),
                () -> assertEquals("refresh-token", cookie.getValue()),
                () -> assertTrue(cookie.isHttpOnly()),
                () -> assertFalse(cookie.isSecure()),
                () -> assertEquals("/api/v1/auth", cookie.getPath()),
                () -> assertEquals("lax", cookie.getSameSite()),
                () -> assertEquals(REFRESH_TOKEN_EXPIRATION, cookie.getMaxAge())
        );
    }

    @Test
    @DisplayName("Refresh Token 만료 쿠키는 동일한 속성과 0초의 Max-Age를 사용한다")
    void shouldCreateExpiredRefreshTokenCookie() {
        ResponseCookie cookie = tokenCookieManager.expire();

        assertAll(
                () -> assertEquals("refreshToken", cookie.getName()),
                () -> assertEquals("", cookie.getValue()),
                () -> assertTrue(cookie.isHttpOnly()),
                () -> assertFalse(cookie.isSecure()),
                () -> assertEquals("/api/v1/auth", cookie.getPath()),
                () -> assertEquals("lax", cookie.getSameSite()),
                () -> assertEquals(Duration.ZERO, cookie.getMaxAge())
        );
    }
}
