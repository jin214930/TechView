package com.ward.techview.common.security;

import org.springframework.security.core.AuthenticatedPrincipal;

public record CustomUserDetails(
        Long id,
        String email
) implements AuthenticatedPrincipal {
    @Override
    public String getName() {
        return email;
    }
}
