package com.ward.techview.account.auth.controller;

import com.ward.techview.account.auth.dto.*;
import com.ward.techview.account.auth.response.AuthResponseCode;
import com.ward.techview.account.auth.service.AuthService;
import com.ward.techview.account.auth.token.RefreshTokenCookieManager;
import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.common.response.ApiResponse;
import com.ward.techview.common.response.CommonResponseCode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final RefreshTokenCookieManager refreshTokenCookieManager;

    @PostMapping("/signup")
    public ApiResponse<Void> signUp(@Valid @RequestBody SignUpRequest request) {
        authService.signUp(request);
        return ApiResponse.from(AuthResponseCode.SIGNUP_SUCCESS);
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            @CookieValue(name = "refreshToken", required = false) String existingRefreshToken,
            HttpServletResponse response
    ) {
        LoginResult result = authService.login(request, existingRefreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieManager.create(result.refreshToken()).toString());
        return ApiResponse.from(AuthResponseCode.LOGIN_SUCCESS, result.loginResponse());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieManager.expire().toString());
        return ApiResponse.from(AuthResponseCode.LOGOUT_SUCCESS);
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(CommonResponseCode.AUTHENTICATION_REQUIRED);
        }

        TokenReissueResult result = authService.reissueTokens(refreshToken);

        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieManager.create(result.refreshToken()).toString());

        return ApiResponse.from(CommonResponseCode.SUCCESS, result.tokenResponse());
    }
}
