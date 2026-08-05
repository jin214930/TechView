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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "회원가입 성공")
    public ApiResponse<Void> signUp(@Valid @RequestBody SignUpRequest request) {
        authService.signUp(request);
        return ApiResponse.from(AuthResponseCode.SIGNUP_SUCCESS);
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            @Parameter(name = "refreshToken", in = ParameterIn.COOKIE, description = "기존 세션의 Refresh Token")
            @CookieValue(name = "refreshToken", required = false) String existingRefreshToken,
            HttpServletResponse response
    ) {
        LoginResult result = authService.login(request, existingRefreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieManager.create(result.refreshToken()).toString());
        return ApiResponse.from(AuthResponseCode.LOGIN_SUCCESS, result.loginResponse());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @Parameter(name = "refreshToken", in = ParameterIn.COOKIE, description = "폐기할 Refresh Token")
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieManager.expire().toString());
        return ApiResponse.from(AuthResponseCode.LOGOUT_SUCCESS);
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(
            @Parameter(name = "refreshToken", in = ParameterIn.COOKIE, description = "토큰 재발급용 Refresh Token", required = true)
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
