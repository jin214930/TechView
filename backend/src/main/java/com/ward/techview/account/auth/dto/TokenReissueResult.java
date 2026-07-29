package com.ward.techview.account.auth.dto;

public record TokenReissueResult(
        String refreshToken,
        TokenResponse tokenResponse
) {
}
