package com.ward.techview.voice.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioObjectKeyGeneratorTest {

    private final AudioObjectKeyGenerator generator = new AudioObjectKeyGenerator();

    @Test
    @DisplayName("세션과 턴 경로 아래에 UUID 객체 키를 생성한다")
    void shouldGenerateObjectKeyWithSessionTurnAndUuid() {
        String objectKey = generator.generate(10L, 100L);

        assertTrue(objectKey.matches(
                "interviews/10/turns/100/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
        ));
    }

    @Test
    @DisplayName("같은 세션과 턴에도 서로 다른 객체 키를 생성한다")
    void shouldGenerateUniqueObjectKeys() {
        String first = generator.generate(10L, 100L);
        String second = generator.generate(10L, 100L);

        assertNotEquals(first, second);
    }
}
