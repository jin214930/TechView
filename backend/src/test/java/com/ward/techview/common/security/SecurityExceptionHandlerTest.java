package com.ward.techview.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SecurityExceptionHandlerTest {

    private ObjectMapper objectMapper;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("인증되지 않은 요청은 HTTP 401과 공통 인증 오류 응답을 반환한다")
    void shouldReturnUnauthorizedResponseForUnauthenticatedRequest() throws Exception {
        CustomAuthenticationEntryPoint entryPoint = new CustomAuthenticationEntryPoint(objectMapper);

        entryPoint.commence(
                request,
                response,
                new InsufficientAuthenticationException("authentication required")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals(401, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());
        assertEquals(1002, body.get("code").asInt());
        assertEquals("인증이 필요합니다.", body.get("message").asString());
        assertFalse(body.has("httpStatus"));
    }

    @Test
    @DisplayName("권한이 부족한 요청은 HTTP 403과 공통 인가 오류 응답을 반환한다")
    void shouldReturnForbiddenResponseForAccessDeniedRequest() throws Exception {
        CustomAccessDeniedHandler accessDeniedHandler = new CustomAccessDeniedHandler(objectMapper);

        accessDeniedHandler.handle(
                request,
                response,
                new AccessDeniedException("access denied")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals(403, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());
        assertEquals(1003, body.get("code").asInt());
        assertEquals("접근 권한이 없습니다.", body.get("message").asString());
        assertFalse(body.has("httpStatus"));
    }
}
