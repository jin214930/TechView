package com.ward.techview.voice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FailedAudioCleanupPropertiesTest {

    @Test
    @DisplayName("실패 음성 정리 설정을 생성한다")
    void createsCleanupProperties() {
        FailedAudioCleanupProperties properties =
                new FailedAudioCleanupProperties(
                        "0 0 4 * * *",
                        "Asia/Seoul",
                        100
                );

        assertAll(
                () -> assertEquals("0 0 4 * * *", properties.cron()),
                () -> assertEquals("Asia/Seoul", properties.timeZone()),
                () -> assertEquals(100, properties.batchSize())
        );
    }

    @Test
    @DisplayName("잘못된 실패 음성 정리 설정을 거부한다")
    void rejectsInvalidCleanupProperties() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new FailedAudioCleanupProperties(
                                " ",
                                "Asia/Seoul",
                                100
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new FailedAudioCleanupProperties(
                                "0 0 4 * * *",
                                " ",
                                100
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new FailedAudioCleanupProperties(
                                "0 0 4 * * *",
                                "Asia/Seoul",
                                0
                        )
                )
        );
    }
}
