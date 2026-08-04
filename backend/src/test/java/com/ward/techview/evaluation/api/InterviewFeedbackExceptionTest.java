package com.ward.techview.evaluation.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InterviewFeedbackExceptionTest {

    @Test
    @DisplayName("종합 피드백 실패 유형과 원인 예외를 보존한다")
    void preservesFailureAndCause() {
        RuntimeException cause = new RuntimeException("원인");

        InterviewFeedbackException exception = new InterviewFeedbackException(
                InterviewFeedbackFailure.TEMPORARY_FAILURE,
                "종합 피드백 생성에 실패했습니다.",
                cause
        );

        assertAll(
                () -> assertEquals(InterviewFeedbackFailure.TEMPORARY_FAILURE, exception.getFailure()),
                () -> assertEquals("종합 피드백 생성에 실패했습니다.", exception.getMessage()),
                () -> assertSame(cause, exception.getCause())
        );
    }

    @Test
    @DisplayName("종합 피드백 실패 유형은 필수이다")
    void rejectsNullFailure() {
        assertThrows(
                NullPointerException.class,
                () -> new InterviewFeedbackException(null, "실패", null)
        );
    }
}
