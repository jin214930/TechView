package com.ward.techview.account.auth.controller;

import com.ward.techview.account.auth.dto.SignUpRequest;
import com.ward.techview.account.auth.response.AuthResponseCode;
import com.ward.techview.account.auth.service.AuthService;
import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.common.exception.GlobalExceptionHandler;
import com.ward.techview.common.response.ApiResponseAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new AuthController(authService))
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
}
