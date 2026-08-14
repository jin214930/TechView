package com.ward.techview.evaluation.entity;

import com.ward.techview.common.entity.BaseEntity;
import com.ward.techview.evaluation.api.feedback.CategoryFeedback;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewFeedback extends BaseEntity {
    private static final int MIN_FEEDBACK_ITEM_COUNT = 1;
    private static final int MAX_FEEDBACK_ITEM_COUNT = 3;
    private static final int CATEGORY_FEEDBACK_COUNT = 5;

    /*
     * DB FK ON DELETE CASCADE:
     * 면접 세션 삭제 시 종합 피드백도 함께 삭제된다.
     */
    @Column(nullable = false, unique = true)
    private Long interviewSessionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String overallSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSONB")
    private List<String> strengths;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSONB")
    private List<String> improvements;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSONB")
    private List<CategoryFeedback> categoryFeedbacks;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSONB")
    private List<String> recommendedTopics;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false, length = 100)
    private String promptVersion;

    private InterviewFeedback(
            Long interviewSessionId,
            String overallSummary,
            List<String> strengths,
            List<String> improvements,
            List<CategoryFeedback> categoryFeedbacks,
            List<String> recommendedTopics,
            String model,
            String promptVersion
    ) {
        this.interviewSessionId = Objects.requireNonNull(
                interviewSessionId,
                "면접 세션 ID는 필수입니다."
        );
        this.overallSummary = requireText(overallSummary, "전체 총평은 필수입니다.");
        this.strengths = validateFeedbackItems(strengths, "잘한 점");
        this.improvements = validateFeedbackItems(improvements, "개선할 점");
        this.categoryFeedbacks = validateCategoryFeedbacks(categoryFeedbacks);
        this.recommendedTopics = validateFeedbackItems(recommendedTopics, "추천 학습 주제");
        this.model = requireText(model, "모델명은 필수입니다.");
        this.promptVersion = requireText(promptVersion, "프롬프트 버전은 필수입니다.");
    }

    public static InterviewFeedback create(
            Long interviewSessionId,
            String overallSummary,
            List<String> strengths,
            List<String> improvements,
            List<CategoryFeedback> categoryFeedbacks,
            List<String> recommendedTopics,
            String model,
            String promptVersion
    ) {
        return new InterviewFeedback(
                interviewSessionId,
                overallSummary,
                strengths,
                improvements,
                categoryFeedbacks,
                recommendedTopics,
                model,
                promptVersion
        );
    }

    private static List<String> validateFeedbackItems(List<String> items, String fieldName) {
        Objects.requireNonNull(items, fieldName + " 목록은 필수입니다.");

        if (items.size() < MIN_FEEDBACK_ITEM_COUNT || items.size() > MAX_FEEDBACK_ITEM_COUNT) {
            throw new IllegalArgumentException(fieldName + "은 " + MIN_FEEDBACK_ITEM_COUNT + "개 이상 " + MAX_FEEDBACK_ITEM_COUNT + "개 이하로 저장해야 합니다.");
        }

        return items.stream()
                .map(item -> requireText(item, fieldName + " 내용은 비어 있을 수 없습니다."))
                .toList();
    }

    private static List<CategoryFeedback> validateCategoryFeedbacks(List<CategoryFeedback> feedbacks) {
        Objects.requireNonNull(feedbacks, "카테고리별 피드백 목록은 필수입니다.");

        if (feedbacks.size() != CATEGORY_FEEDBACK_COUNT) {
            throw new IllegalArgumentException("카테고리별 피드백은 정확히 " + CATEGORY_FEEDBACK_COUNT + "개여야 합니다.");
        }

        List<CategoryFeedback> copiedFeedbacks = feedbacks.stream()
                .map(feedback -> Objects.requireNonNull(feedback, "카테고리별 피드백은 null일 수 없습니다."))
                .toList();

        long categoryCount = copiedFeedbacks.stream()
                .map(CategoryFeedback::category)
                .distinct()
                .count();

        if (categoryCount != CATEGORY_FEEDBACK_COUNT) {
            throw new IllegalArgumentException("카테고리별 피드백의 카테고리는 중복될 수 없습니다.");
        }

        return copiedFeedbacks;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.strip();
    }
}
