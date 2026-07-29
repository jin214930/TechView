package com.ward.techview.common.security;

import java.time.OffsetDateTime;

public record IssuedToken(
        String value,
        OffsetDateTime expiresAt
) {
}
