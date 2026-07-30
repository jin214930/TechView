package com.ward.techview.account.auth.token;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

@Component
public class RefreshTokenHasher {
    private static final String ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_SECRET_KEY_BYTES = 32;

    private final SecretKeySpec secretKey;

    public RefreshTokenHasher(
            @Value("${custom.auth.refresh-token.hash-secret-key}")
            String secretKey
    ) {
        validateSecretKey(secretKey);

        this.secretKey = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8),
                ALGORITHM
        );
    }

    public String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException(
                    "Refresh Token은 비어 있을 수 없습니다."
            );
        }

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secretKey);

            byte[] hash = mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Refresh Token 해시 생성에 실패했습니다.", e);
        }
    }

    private static void validateSecretKey(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("Refresh Token 해시 비밀키는 비어 있을 수 없습니다.");
        }

        int keyLength = secretKey.getBytes(StandardCharsets.UTF_8).length;

        if (keyLength < MINIMUM_SECRET_KEY_BYTES) {
            throw new IllegalArgumentException("Refresh Token 해시 비밀키는 UTF-8 기준 32바이트 이상이어야 합니다.");
        }
    }
}
