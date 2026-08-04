package com.ward.techview.integration.feedback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiInterviewFeedbackPropertiesTest {

    @Test
    @DisplayName("종합 피드백 OpenAI 기능 설정을 생성한다")
    void createsProperties() {
        OpenAiInterviewFeedbackProperties properties =
                new OpenAiInterviewFeedbackProperties(
                        "gpt-5.6-luna",
                        "interview-feedback-v1",
                        2000,
                        Duration.ofSeconds(90)
                );

        assertAll(
                () -> assertEquals("gpt-5.6-luna", properties.model()),
                () -> assertEquals("interview-feedback-v1", properties.promptVersion()),
                () -> assertEquals(2000, properties.maxOutputTokens()),
                () -> assertEquals(Duration.ofSeconds(90), properties.timeout())
        );
    }

    @Test
    @DisplayName("종합 피드백 요청 제한 시간은 0보다 커야 한다")
    void rejectsNonPositiveTimeout() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createProperties(Duration.ZERO)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createProperties(Duration.ofSeconds(-1))
                )
        );
    }

    private static OpenAiInterviewFeedbackProperties createProperties(
            Duration timeout
    ) {
        return new OpenAiInterviewFeedbackProperties(
                "gpt-5.6-luna",
                "interview-feedback-v1",
                2000,
                timeout
        );
    }
}
