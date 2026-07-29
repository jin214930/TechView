package com.ward.techview.account.auth.dto;

public record LoginResult(
        String refreshToken,
        LoginResponse loginResponse
) {
}
