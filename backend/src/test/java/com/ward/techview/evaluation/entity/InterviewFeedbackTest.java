package com.ward.techview.evaluation.entity;

import com.ward.techview.evaluation.api.feedback.CategoryFeedback;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InterviewFeedbackTest {

    private static final Long INTERVIEW_SESSION_ID = 100L;

    @Test
    @DisplayName("면접 종합 피드백을 생성하고 문자열 앞뒤 공백을 제거한다")
    void createsInterviewFeedback() {
        InterviewFeedback feedback = InterviewFeedback.create(
                INTERVIEW_SESSION_ID,
                "  전반적으로 핵심 개념을 잘 설명했습니다.  ",
                List.of("  개념 설명이 명확합니다.  "),
                List.of("  예시를 더 구체화해 보세요.  "),
                categoryFeedbacks(),
                List.of("  Java 동시성  "),
                "  gpt-5-mini  ",
                "  interview-feedback-v1  "
        );

        assertAll(
                () -> assertEquals(INTERVIEW_SESSION_ID, feedback.getInterviewSessionId()),
                () -> assertEquals("전반적으로 핵심 개념을 잘 설명했습니다.", feedback.getOverallSummary()),
                () -> assertEquals(List.of("개념 설명이 명확합니다."), feedback.getStrengths()),
                () -> assertEquals(List.of("예시를 더 구체화해 보세요."), feedback.getImprovements()),
                () -> assertEquals(categoryFeedbacks(), feedback.getCategoryFeedbacks()),
                () -> assertEquals(List.of("Java 동시성"), feedback.getRecommendedTopics()),
                () -> assertEquals("gpt-5-mini", feedback.getModel()),
                () -> assertEquals("interview-feedback-v1", feedback.getPromptVersion())
        );
    }

    @Test
    @DisplayName("면접 세션 ID가 없으면 종합 피드백을 생성할 수 없다")
    void rejectsNullInterviewSessionId() {
        assertThrows(
                NullPointerException.class,
                () -> createFeedback(null, List.of("강점"), List.of("개선점"), categoryFeedbacks(), List.of("학습 주제"))
        );
    }

    @Test
    @DisplayName("필수 문자열이 비어 있으면 종합 피드백을 생성할 수 없다")
    void rejectsBlankRequiredText() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> InterviewFeedback.create(
                                INTERVIEW_SESSION_ID,
                                " ",
                                List.of("강점"),
                                List.of("개선점"),
                                categoryFeedbacks(),
                                List.of("학습 주제"),
                                "model",
                                "prompt-v1"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> InterviewFeedback.create(
                                INTERVIEW_SESSION_ID,
                                "총평",
                                List.of("강점"),
                                List.of("개선점"),
                                categoryFeedbacks(),
                                List.of("학습 주제"),
                                null,
                                "prompt-v1"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> InterviewFeedback.create(
                                INTERVIEW_SESSION_ID,
                                "총평",
                                List.of("강점"),
                                List.of("개선점"),
                                categoryFeedbacks(),
                                List.of("학습 주제"),
                                "model",
                                " "
                        )
                )
        );
    }

    @Test
    @DisplayName("강점과 개선점과 추천 학습 주제는 각각 한 개 이상 세 개 이하여야 한다")
    void rejectsInvalidFeedbackItemCount() {
        List<String> tooManyItems = List.of("첫 번째", "두 번째", "세 번째", "네 번째");

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createFeedback(INTERVIEW_SESSION_ID, List.of(), List.of("개선점"), categoryFeedbacks(), List.of("학습 주제"))
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createFeedback(INTERVIEW_SESSION_ID, List.of("강점"), tooManyItems, categoryFeedbacks(), List.of("학습 주제"))
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createFeedback(INTERVIEW_SESSION_ID, List.of("강점"), List.of("개선점"), categoryFeedbacks(), tooManyItems)
                )
        );
    }

    @Test
    @DisplayName("피드백 목록에 빈 내용이 있으면 종합 피드백을 생성할 수 없다")
    void rejectsBlankFeedbackItem() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createFeedback(INTERVIEW_SESSION_ID, List.of(" "), List.of("개선점"), categoryFeedbacks(), List.of("학습 주제"))
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createFeedback(INTERVIEW_SESSION_ID, List.of("강점"), List.of(" "), categoryFeedbacks(), List.of("학습 주제"))
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createFeedback(INTERVIEW_SESSION_ID, List.of("강점"), List.of("개선점"), categoryFeedbacks(), List.of(" "))
                )
        );
    }

    @Test
    @DisplayName("카테고리별 피드백은 서로 다른 카테고리 다섯 개여야 한다")
    void rejectsInvalidCategoryFeedbacks() {
        List<CategoryFeedback> duplicateCategories = List.of(
                categoryFeedback("JAVA"),
                categoryFeedback("JAVA"),
                categoryFeedback("DATABASE"),
                categoryFeedback("OPERATING_SYSTEM"),
                categoryFeedback("NETWORK")
        );

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createFeedback(INTERVIEW_SESSION_ID, List.of("강점"), List.of("개선점"), categoryFeedbacks().subList(0, 4), List.of("학습 주제"))
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createFeedback(INTERVIEW_SESSION_ID, List.of("강점"), List.of("개선점"), duplicateCategories, List.of("학습 주제"))
                )
        );
    }

    private static InterviewFeedback createFeedback(
            Long interviewSessionId,
            List<String> strengths,
            List<String> improvements,
            List<CategoryFeedback> categoryFeedbacks,
            List<String> recommendedTopics
    ) {
        return InterviewFeedback.create(
                interviewSessionId,
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
        return new CategoryFeedback(category, "평가", "학습 추천");
    }
}
