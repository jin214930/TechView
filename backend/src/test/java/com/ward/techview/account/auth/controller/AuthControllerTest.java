package com.ward.techview.account.auth.controller;

import com.ward.techview.account.auth.dto.LoginRequest;
import com.ward.techview.account.auth.dto.LoginResponse;
import com.ward.techview.account.auth.dto.LoginResult;
import com.ward.techview.account.auth.dto.SignUpRequest;
import com.ward.techview.account.auth.dto.TokenReissueResult;
import com.ward.techview.account.auth.dto.TokenResponse;
import com.ward.techview.account.auth.response.AuthResponseCode;
import com.ward.techview.account.auth.service.AuthService;
import com.ward.techview.account.auth.token.RefreshTokenCookieManager;
import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.common.exception.GlobalExceptionHandler;
import com.ward.techview.common.response.ApiResponseAdvice;
import com.ward.techview.common.response.CommonResponseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private RefreshTokenCookieManager refreshTokenCookieManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new AuthController(authService, refreshTokenCookieManager))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResponseAdvice())
                .build();
    }

    @Test
    @DisplayName("회원가입에 성공하면 HTTP 201과 성공 응답을 반환한다")
    void shouldReturnCreatedWhenSignUpSucceeds() throws Exception {
        SignUpRequest request = new SignUpRequest("user@example.com", "password123!", "사용자");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password123!",
                                  "nickname": "사용자"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(authService).signUp(request);
    }

    @Test
    @DisplayName("회원가입 요청 값 검증에 실패하면 HTTP 400과 검증 메시지를 반환한다")
    void shouldReturnBadRequestWhenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "short1!",
                                  "nickname": "사용자"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("비밀번호는 8자 이상 64자 이하로 입력해주세요."));

        verify(authService, never()).signUp(any(SignUpRequest.class));
    }

    @Test
    @DisplayName("이메일이 중복되면 HTTP 409와 이메일 중복 코드를 반환한다")
    void shouldReturnConflictWhenEmailAlreadyExists() throws Exception {
        doThrow(new BusinessException(AuthResponseCode.EMAIL_CONFLICT))
                .when(authService)
                .signUp(any(SignUpRequest.class));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password123!",
                                  "nickname": "사용자"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(2000))
                .andExpect(jsonPath("$.message").value("이미 존재하는 이메일입니다."));
    }

    @Test
    @DisplayName("로그인에 성공하면 Access Token과 회원 정보 및 Refresh Token 쿠키를 반환한다")
    void shouldReturnLoginResponseAndRefreshTokenCookieWhenLoginSucceeds() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "password123!");
        LoginResponse loginResponse = new LoginResponse(
                "access-token",
                1L,
                "user@example.com",
                "사용자"
        );
        LoginResult result = new LoginResult("new-refresh-token", loginResponse);
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", "new-refresh-token")
                .httpOnly(true)
                .path("/api/v1/auth")
                .maxAge(2_592_000)
                .build();

        when(authService.login(request, "existing-refresh-token")).thenReturn(result);
        when(refreshTokenCookieManager.create("new-refresh-token")).thenReturn(refreshTokenCookie);

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(new Cookie("refreshToken", "existing-refresh-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        allOf(
                                containsString("refreshToken=new-refresh-token"),
                                containsString("Path=/api/v1/auth"),
                                containsString("Max-Age=2592000"),
                                containsString("HttpOnly")
                        )
                ))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("로그인이 완료되었습니다."))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("사용자"));

        verify(authService).login(request, "existing-refresh-token");
        verify(refreshTokenCookieManager).create("new-refresh-token");
    }

    @Test
    @DisplayName("로그인 요청 값이 비어 있으면 HTTP 400과 검증 메시지를 반환한다")
    void shouldReturnBadRequestWhenLoginRequestIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {
                                  "email": "",
                                  "password": "password123!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("이메일을 입력해주세요."));

        verify(authService, never()).login(any(LoginRequest.class), any());
    }

    @Test
    @DisplayName("로그인 정보가 일치하지 않으면 HTTP 401과 로그인 실패 코드를 반환한다")
    void shouldReturnUnauthorizedWhenLoginFails() throws Exception {
        doThrow(new BusinessException(AuthResponseCode.LOGIN_FAILED))
                .when(authService)
                .login(any(LoginRequest.class), org.mockito.ArgumentMatchers.isNull());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(2001))
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 일치하지 않습니다."));

        verify(refreshTokenCookieManager, never()).create(any());
    }

    @Test
    @DisplayName("로그아웃하면 Refresh Token을 폐기하고 만료 쿠키를 반환한다")
    void shouldRevokeRefreshTokenAndExpireCookieWhenLogoutSucceeds() throws Exception {
        ResponseCookie expiredCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
        when(refreshTokenCookieManager.expire()).thenReturn(expiredCookie);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refreshToken", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        allOf(
                                containsString("refreshToken="),
                                containsString("Path=/api/v1/auth"),
                                containsString("Max-Age=0"),
                                containsString("HttpOnly")
                        )
                ))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("로그아웃이 완료되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(authService).logout("refresh-token");
        verify(refreshTokenCookieManager).expire();
    }

    @Test
    @DisplayName("Refresh Token 쿠키가 없어도 로그아웃에 성공하고 만료 쿠키를 반환한다")
    void shouldCompleteLogoutAndExpireCookieWhenRefreshTokenIsMissing() throws Exception {
        ResponseCookie expiredCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
        when(refreshTokenCookieManager.expire()).thenReturn(expiredCookie);

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("Max-Age=0")
                ))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("로그아웃이 완료되었습니다."));

        verify(authService).logout(null);
        verify(refreshTokenCookieManager).expire();
    }

    @Test
    @DisplayName("토큰 재발급에 성공하면 새 Access Token과 Refresh Token 쿠키를 반환한다")
    void shouldReturnNewTokensWhenRefreshSucceeds() throws Exception {
        TokenReissueResult result = new TokenReissueResult(
                "new-refresh-token",
                new TokenResponse("new-access-token")
        );
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", "new-refresh-token")
                .httpOnly(true)
                .path("/api/v1/auth")
                .maxAge(2_592_000)
                .build();
        when(authService.reissueTokens("refresh-token")).thenReturn(result);
        when(refreshTokenCookieManager.create("new-refresh-token")).thenReturn(refreshTokenCookie);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        allOf(
                                containsString("refreshToken=new-refresh-token"),
                                containsString("Path=/api/v1/auth"),
                                containsString("Max-Age=2592000"),
                                containsString("HttpOnly")
                        )
                ))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));

        verify(authService).reissueTokens("refresh-token");
        verify(refreshTokenCookieManager).create("new-refresh-token");
    }

    @Test
    @DisplayName("Refresh Token 쿠키가 없으면 HTTP 401을 반환한다")
    void shouldReturnUnauthorizedWhenRefreshTokenCookieIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));

        verify(authService, never()).reissueTokens(any());
        verify(refreshTokenCookieManager, never()).create(any());
    }

    @Test
    @DisplayName("유효하지 않은 Refresh Token이면 HTTP 401을 반환하고 쿠키를 발급하지 않는다")
    void shouldReturnUnauthorizedWhenRefreshTokenIsInvalid() throws Exception {
        doThrow(new BusinessException(CommonResponseCode.AUTHENTICATION_REQUIRED))
                .when(authService)
                .reissueTokens("invalid-token");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", "invalid-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));

        verify(refreshTokenCookieManager, never()).create(any());
    }
}
