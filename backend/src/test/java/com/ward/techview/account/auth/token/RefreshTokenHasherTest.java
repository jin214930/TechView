package com.ward.techview.account.auth.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenHasherTest {

    private static final String SECRET_KEY = "TECHVIEW_REFRESH_TOKEN_HASH_SECRET_KEY_FOR_TESTS";

    private final RefreshTokenHasher refreshTokenHasher = new RefreshTokenHasher(SECRET_KEY);

    @Test
    @DisplayName("같은 Refresh Token은 항상 같은 해시를 생성한다")
    void shouldGenerateSameHashForSameToken() {
        String first = refreshTokenHasher.hash("refresh-token");
        String second = refreshTokenHasher.hash("refresh-token");

        assertEquals(first, second);
    }

    @Test
    @DisplayName("다른 Refresh Token은 서로 다른 해시를 생성한다")
    void shouldGenerateDifferentHashesForDifferentTokens() {
        String first = refreshTokenHasher.hash("first-refresh-token");
        String second = refreshTokenHasher.hash("second-refresh-token");

        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("해시는 64자리 소문자 16진수이며 원문을 포함하지 않는다")
    void shouldGenerateLowercaseHexHashWithoutRawToken() {
        String rawToken = "refresh-token";

        String hash = refreshTokenHasher.hash(rawToken);

        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertFalse(hash.contains(rawToken));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("Refresh Token 원문이 비어 있으면 예외가 발생한다")
    void shouldRejectBlankRawToken(String rawToken) {
        assertThrows(IllegalArgumentException.class, () -> refreshTokenHasher.hash(rawToken));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("해시 비밀키가 비어 있으면 예외가 발생한다")
    void shouldRejectBlankSecretKey(String secretKey) {
        assertThrows(IllegalArgumentException.class, () -> new RefreshTokenHasher(secretKey));
    }

    @Test
    @DisplayName("해시 비밀키가 UTF-8 기준 32바이트 미만이면 예외가 발생한다")
    void shouldRejectShortSecretKey() {
        String shortSecretKey = "a".repeat(31);

        assertThrows(IllegalArgumentException.class, () -> new RefreshTokenHasher(shortSecretKey));
    }
}
