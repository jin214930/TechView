package com.ward.techview.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomUserDetailsTest {

    @Test
    @DisplayName("인증 principal의 이름으로 이메일을 반환한다")
    void shouldReturnEmailAsPrincipalName() {
        CustomUserDetails principal = new CustomUserDetails(1L, "user@example.com");

        assertEquals("user@example.com", principal.getName());
    }
}
