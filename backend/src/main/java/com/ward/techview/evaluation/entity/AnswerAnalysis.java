package com.ward.techview.evaluation.entity;

import com.ward.techview.common.entity.BaseEntity;
import com.ward.techview.evaluation.api.FollowUpDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
public class AnswerAnalysis extends BaseEntity {
    private static final int MAX_FEEDBACK_ITEM_COUNT = 3;

    /*
     * DB FK ON DELETE CASCADE:
     * 답변 삭제 시 해당 답변의 분석 결과도 함께 삭제한다.
     */
    @Column(nullable = false, unique = true)
    private Long answerId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSONB")
    private List<String> strengths;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSONB")
    private List<String> improvements;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FollowUpDecision followUpDecision;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String followUpReason;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false, length = 100)
    private String promptVersion;

    private AnswerAnalysis(
            Long answerId,
            String summary,
            List<String> strengths,
            List<String> improvements,
            FollowUpDecision followUpDecision,
            String followUpReason,
            String model,
            String promptVersion
    ) {
        this.answerId = Objects.requireNonNull(answerId, "답변 ID는 필수입니다.");
        this.summary = requireText(summary, "답변 요약은 필수입니다.");
        this.strengths = validateFeedbackItems(strengths, "잘한 점");
        this.improvements = validateFeedbackItems(improvements, "개선할 점");
        this.followUpDecision = Objects.requireNonNull(followUpDecision, "꼬리 질문 결정은 필수입니다.");
        this.followUpReason = requireText(followUpReason, "꼬리 질문 판단 근거는 필수입니다.");
        this.model = requireText(model, "모델명은 필수입니다.");
        this.promptVersion = requireText(promptVersion, "프롬프트 버전은 필수입니다.");
    }

    public static AnswerAnalysis create(
            Long answerId,
            String summary,
            List<String> strengths,
            List<String> improvements,
            FollowUpDecision followUpDecision,
            String followUpReason,
            String model,
            String promptVersion
    ) {
        return new AnswerAnalysis(answerId, summary, strengths, improvements, followUpDecision, followUpReason, model, promptVersion);
    }

    private static List<String> validateFeedbackItems(List<String> items, String fieldName) {
        Objects.requireNonNull(items, fieldName + " 목록은 필수입니다.");

        if (items.size() > MAX_FEEDBACK_ITEM_COUNT) {
            throw new IllegalArgumentException(fieldName + "은 최대 " + MAX_FEEDBACK_ITEM_COUNT + "개까지 저장할 수 있습니다.");
        }

        return items.stream()
                .map(item -> requireText(item, fieldName + " 내용은 비어 있을 수 없습니다."))
                .toList();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.strip();
    }
}
