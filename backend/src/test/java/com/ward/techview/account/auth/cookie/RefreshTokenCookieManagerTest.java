package com.ward.techview.account.auth.cookie;

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

class RefreshTokenCookieManagerTest {

    private static final Duration REFRESH_TOKEN_EXPIRATION = Duration.ofDays(30);

    private RefreshTokenCookieManager refreshTokenCookieManager;

    @BeforeEach
    void setUp() {
        refreshTokenCookieManager = new RefreshTokenCookieManager();
        ReflectionTestUtils.setField(
                refreshTokenCookieManager,
                "refreshTokenExpirationTime",
                REFRESH_TOKEN_EXPIRATION
        );
        ReflectionTestUtils.setField(refreshTokenCookieManager, "httpOnly", true);
        ReflectionTestUtils.setField(refreshTokenCookieManager, "path", "/api/v1/auth");
        ReflectionTestUtils.setField(refreshTokenCookieManager, "secure", false);
        ReflectionTestUtils.setField(refreshTokenCookieManager, "sameSite", "lax");
    }

    @Test
    @DisplayName("Refresh Token 쿠키에 보안 속성과 만료 시간을 설정한다")
    void shouldCreateRefreshTokenCookieWithConfiguredAttributes() {
        ResponseCookie cookie = refreshTokenCookieManager.create("refresh-token");

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
        ResponseCookie cookie = refreshTokenCookieManager.expire();

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
