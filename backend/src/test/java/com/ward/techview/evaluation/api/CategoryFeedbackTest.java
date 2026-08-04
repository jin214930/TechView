package com.ward.techview.evaluation.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CategoryFeedbackTest {

    @Test
    @DisplayName("카테고리별 피드백을 생성하고 문자열 앞뒤 공백을 제거한다")
    void createsCategoryFeedback() {
        CategoryFeedback feedback = new CategoryFeedback(
                "  JAVA  ",
                "  핵심 개념을 정확히 설명했습니다.  ",
                "  동시성 개념을 추가로 학습해 보세요.  "
        );

        assertAll(
                () -> assertEquals("JAVA", feedback.category()),
                () -> assertEquals("핵심 개념을 정확히 설명했습니다.", feedback.assessment()),
                () -> assertEquals("동시성 개념을 추가로 학습해 보세요.", feedback.recommendation())
        );
    }

    @Test
    @DisplayName("필수 문자열이 비어 있으면 카테고리별 피드백을 생성할 수 없다")
    void rejectsBlankRequiredText() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CategoryFeedback(" ", "평가", "추천")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CategoryFeedback("JAVA", null, "추천")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CategoryFeedback("JAVA", "평가", " ")
                )
        );
    }
}
