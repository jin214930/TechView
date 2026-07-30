package com.ward.techview.account.auth.token;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshTokenCookieManager {
    @Value("${custom.jwt.refresh-token.expiration-time}")
    private Duration refreshTokenExpirationTime;

    @Value("${custom.cookie.token.http-only}")
    private boolean httpOnly;

    @Value("${custom.cookie.token.path}")
    private String path;

    @Value("${custom.cookie.token.secure}")
    private boolean secure;

    @Value("${custom.cookie.token.same-site}")
    private String sameSite;

    public ResponseCookie create(String refreshToken) {
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(httpOnly)
                .secure(secure)
                .path(path)
                .sameSite(sameSite)
                .maxAge(refreshTokenExpirationTime)
                .build();
    }

    public ResponseCookie expire() {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(httpOnly)
                .secure(secure)
                .path(path)
                .sameSite(sameSite)
                .maxAge(Duration.ZERO)
                .build();
    }
}
