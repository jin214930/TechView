package com.ward.techview.evaluation.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InterviewFeedbackResultTest {

    @Test
    @DisplayName("종합 피드백 결과를 생성하고 문자열 앞뒤 공백을 제거한다")
    void createsFeedbackResult() {
        InterviewFeedbackResult result = new InterviewFeedbackResult(
                "  전체 총평  ",
                List.of("  강점  "),
                List.of("  개선점  "),
                categoryFeedbacks(),
                List.of("  추천 주제  "),
                "  gpt-5-mini  ",
                "  interview-feedback-v1  "
        );

        assertAll(
                () -> assertEquals("전체 총평", result.overallSummary()),
                () -> assertEquals(List.of("강점"), result.strengths()),
                () -> assertEquals(List.of("개선점"), result.improvements()),
                () -> assertEquals(categoryFeedbacks(), result.categoryFeedbacks()),
                () -> assertEquals(List.of("추천 주제"), result.recommendedTopics()),
                () -> assertEquals("gpt-5-mini", result.model()),
                () -> assertEquals("interview-feedback-v1", result.promptVersion())
        );
    }

    @Test
    @DisplayName("강점과 개선점과 추천 주제는 각각 한 개 이상 세 개 이하여야 한다")
    void rejectsInvalidFeedbackItemCount() {
        List<String> tooManyItems = List.of("첫 번째", "두 번째", "세 번째", "네 번째");

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createResult(List.of(), List.of("개선점"), List.of("주제"), categoryFeedbacks())
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createResult(List.of("강점"), tooManyItems, List.of("주제"), categoryFeedbacks())
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createResult(List.of("강점"), List.of("개선점"), List.of(), categoryFeedbacks())
                )
        );
    }

    @Test
    @DisplayName("피드백 목록에는 빈 내용을 포함할 수 없다")
    void rejectsBlankFeedbackItem() {
        assertThrows(
                IllegalArgumentException.class,
                () -> createResult(List.of(" "), List.of("개선점"), List.of("주제"), categoryFeedbacks())
        );
    }

    @Test
    @DisplayName("카테고리별 피드백은 서로 다른 카테고리 다섯 개여야 한다")
    void rejectsInvalidCategoryFeedbacks() {
        List<CategoryFeedback> duplicates = List.of(
                categoryFeedback("JAVA"),
                categoryFeedback("JAVA"),
                categoryFeedback("DATABASE"),
                categoryFeedback("OPERATING_SYSTEM"),
                categoryFeedback("NETWORK")
        );

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createResult(
                                List.of("강점"),
                                List.of("개선점"),
                                List.of("주제"),
                                categoryFeedbacks().subList(0, 4)
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createResult(
                                List.of("강점"),
                                List.of("개선점"),
                                List.of("주제"),
                                duplicates
                        )
                )
        );
    }

    @Test
    @DisplayName("필수 문자열이 비어 있으면 종합 피드백 결과를 생성할 수 없다")
    void rejectsBlankRequiredText() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new InterviewFeedbackResult(
                                " ", List.of("강점"), List.of("개선점"), categoryFeedbacks(), List.of("주제"), "model", "v1"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new InterviewFeedbackResult(
                                "총평", List.of("강점"), List.of("개선점"), categoryFeedbacks(), List.of("주제"), null, "v1"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new InterviewFeedbackResult(
                                "총평", List.of("강점"), List.of("개선점"), categoryFeedbacks(), List.of("주제"), "model", " "
                        )
                )
        );
    }

    private static InterviewFeedbackResult createResult(
            List<String> strengths,
            List<String> improvements,
            List<String> recommendedTopics,
            List<CategoryFeedback> categoryFeedbacks
    ) {
        return new InterviewFeedbackResult(
                "총평",
                strengths,
                improvements,
                categoryFeedbacks,
                recommendedTopics,
                "gpt-5-mini",
                "interview-feedback-v1"
        );
    }

    private static List<CategoryFeedback> categoryFeedbacks() {
        return List.of(
                categoryFeedback("JAVA"),
                categoryFeedback("SPRING"),
                categoryFeedback("DATABASE"),
                categoryFeedback("OPERATING_SYSTEM"),
                categoryFeedback("NETWORK")
        );
    }

    private static CategoryFeedback categoryFeedback(String category) {
        return new CategoryFeedback(category, "평가", "추천");
    }
}
