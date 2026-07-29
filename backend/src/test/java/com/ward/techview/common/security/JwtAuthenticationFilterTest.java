package com.ward.techview.common.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter jwtAuthenticationFilter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtProvider);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 Bearer Token이면 SecurityContext에 인증 정보를 저장한다")
    void shouldSetAuthenticationForValidBearerToken() throws Exception {
        CustomUserDetails principal = new CustomUserDetails(1L, "user@example.com");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer access-token");
        when(jwtProvider.getAccessTokenPrincipal("access-token")).thenReturn(principal);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertAll(
                () -> assertSame(principal, authentication.getPrincipal()),
                () -> assertEquals("user@example.com", authentication.getName()),
                () -> assertNull(authentication.getCredentials()),
                () -> assertTrue(authentication.getAuthorities().isEmpty()),
                () -> assertTrue(authentication.isAuthenticated())
        );
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Bearer 인증 스킴은 대소문자를 구분하지 않는다")
    void shouldAuthenticateWhenBearerSchemeUsesLowercase() throws Exception {
        CustomUserDetails principal = new CustomUserDetails(1L, "user@example.com");
        request.addHeader(HttpHeaders.AUTHORIZATION, "bearer access-token");
        when(jwtProvider.getAccessTokenPrincipal("access-token")).thenReturn(principal);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertSame(principal, authentication.getPrincipal());
        verify(jwtProvider).getAccessTokenPrincipal("access-token");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 익명 상태로 필터 체인을 진행한다")
    void shouldContinueWithoutAuthenticationWhenAuthorizationHeaderIsMissing() throws Exception {
        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtProvider, never()).getAccessTokenPrincipal(org.mockito.ArgumentMatchers.any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Bearer가 아닌 인증 스킴이면 Provider를 호출하지 않는다")
    void shouldIgnoreUnsupportedAuthorizationScheme() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic credentials");

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtProvider, never()).getAccessTokenPrincipal(org.mockito.ArgumentMatchers.any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("빈 Bearer Token이면 익명 상태로 필터 체인을 진행한다")
    void shouldContinueWithoutAuthenticationForEmptyBearerToken() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer ");

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("유효하지 않은 Bearer Token이면 익명 상태로 필터 체인을 진행한다")
    void shouldContinueWithoutAuthenticationForInvalidBearerToken() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        when(jwtProvider.getAccessTokenPrincipal("invalid-token")).thenReturn(null);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}
