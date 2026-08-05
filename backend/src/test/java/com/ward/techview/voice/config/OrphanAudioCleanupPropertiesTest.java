package com.ward.techview.voice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.zone.ZoneRulesException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrphanAudioCleanupPropertiesTest {

    @Test
    @DisplayName("유효한 고아 음성 정리 설정을 생성한다")
    void createsValidProperties() {
        assertDoesNotThrow(() -> properties(Duration.ofHours(24), "Asia/Seoul", 1_000));
    }

    @Test
    @DisplayName("보존 시간이 0 이하면 설정 생성을 거절한다")
    void rejectsNonPositiveGracePeriod() {
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(Duration.ZERO, "Asia/Seoul", 1_000)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(Duration.ofHours(-1), "Asia/Seoul", 1_000)
        );
    }

    @Test
    @DisplayName("페이지 크기가 S3 조회 범위를 벗어나면 설정 생성을 거절한다")
    void rejectsInvalidPageSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(Duration.ofHours(24), "Asia/Seoul", 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(Duration.ofHours(24), "Asia/Seoul", 1_001)
        );
    }

    @Test
    @DisplayName("유효하지 않은 시간대 설정을 거절한다")
    void rejectsInvalidTimeZone() {
        assertThrows(
                ZoneRulesException.class,
                () -> properties(Duration.ofHours(24), "Invalid/Zone", 1_000)
        );
    }

    private static OrphanAudioCleanupProperties properties(
            Duration gracePeriod,
            String timeZone,
            int pageSize
    ) {
        return new OrphanAudioCleanupProperties(
                "interviews/",
                gracePeriod,
                "0 0 5 * * *",
                timeZone,
                pageSize
        );
    }
}
