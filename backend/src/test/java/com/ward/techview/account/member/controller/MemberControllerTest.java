package com.ward.techview.account.member.controller;

import com.ward.techview.account.auth.cookie.RefreshTokenCookieManager;
import com.ward.techview.account.member.dto.MemberResponse;
import com.ward.techview.account.member.response.MemberResponseCode;
import com.ward.techview.account.member.service.MemberService;
import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.common.exception.GlobalExceptionHandler;
import com.ward.techview.common.response.ApiResponseAdvice;
import com.ward.techview.common.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final CustomUserDetails PRINCIPAL =
            new CustomUserDetails(MEMBER_ID, "user@example.com");

    @Mock
    private MemberService memberService;

    @Mock
    private RefreshTokenCookieManager refreshTokenCookieManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, List.of())
        );

        mockMvc = standaloneSetup(new MemberController(memberService, refreshTokenCookieManager))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResponseAdvice())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("내 프로필 조회에 성공하면 회원 정보를 반환한다")
    void shouldReturnMemberProfileWhenGetMeSucceeds() throws Exception {
        MemberResponse response = new MemberResponse(
                MEMBER_ID,
                "user@example.com",
                "사용자"
        );
        when(memberService.getMember(MEMBER_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
                .andExpect(jsonPath("$.data.id").value(MEMBER_ID))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("사용자"));

        verify(memberService).getMember(MEMBER_ID);
    }

    @Test
    @DisplayName("프로필을 조회할 회원이 없으면 HTTP 404를 반환한다")
    void shouldReturnNotFoundWhenGettingMissingMember() throws Exception {
        when(memberService.getMember(MEMBER_ID))
                .thenThrow(new BusinessException(MemberResponseCode.MEMBER_NOT_FOUND));

        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2004))
                .andExpect(jsonPath("$.message").value("회원을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("회원 탈퇴에 성공하면 Refresh Token 쿠키를 만료한다")
    void shouldDeleteMemberAndExpireRefreshTokenCookie() throws Exception {
        ResponseCookie expiredCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
        when(refreshTokenCookieManager.expire()).thenReturn(expiredCookie);

        mockMvc.perform(delete("/api/v1/members/me"))
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
                .andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(memberService).deleteMember(MEMBER_ID);
        verify(refreshTokenCookieManager).expire();
    }

    @Test
    @DisplayName("탈퇴할 회원이 없으면 HTTP 404를 반환하고 쿠키를 만료하지 않는다")
    void shouldReturnNotFoundAndNotExpireCookieWhenDeletingMissingMember() throws Exception {
        doThrow(new BusinessException(MemberResponseCode.MEMBER_NOT_FOUND))
                .when(memberService)
                .deleteMember(MEMBER_ID);

        mockMvc.perform(delete("/api/v1/members/me"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.code").value(2004))
                .andExpect(jsonPath("$.message").value("회원을 찾을 수 없습니다."));

        verify(refreshTokenCookieManager, never()).expire();
    }
}
