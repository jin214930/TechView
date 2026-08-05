package com.ward.techview.interview.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.zone.ZoneRulesException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpiredInterviewCleanupPropertiesTest {

    @Test
    @DisplayName("유효한 만료 면접 정리 설정을 생성한다")
    void createsValidProperties() {
        assertDoesNotThrow(() -> properties(Duration.ofDays(30), "Asia/Seoul", 100));
    }

    @Test
    @DisplayName("보관 기간과 배치 크기는 0보다 커야 한다")
    void rejectsNonPositiveRetentionOrBatchSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(Duration.ZERO, "Asia/Seoul", 100)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(Duration.ofDays(30), "Asia/Seoul", 0)
        );
    }

    @Test
    @DisplayName("유효하지 않은 시간대 설정을 거절한다")
    void rejectsInvalidTimeZone() {
        assertThrows(
                ZoneRulesException.class,
                () -> properties(Duration.ofDays(30), "Invalid/Zone", 100)
        );
    }

    private static ExpiredInterviewCleanupProperties properties(
            Duration retentionPeriod,
            String timeZone,
            int batchSize
    ) {
        return new ExpiredInterviewCleanupProperties(
                retentionPeriod,
                "0 30 4 * * *",
                timeZone,
                batchSize
        );
    }
}
