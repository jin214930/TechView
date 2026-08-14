package com.ward.techview.evaluation.entity;

import com.ward.techview.evaluation.api.analysis.FollowUpDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnswerAnalysisTest {

    private static final Long ANSWER_ID = 100L;

    @Test
    @DisplayName("답변 분석 결과를 생성하고 문자열 양끝 공백을 제거한다")
    void createsAnswerAnalysis() {
        AnswerAnalysis analysis = AnswerAnalysis.create(
                ANSWER_ID,
                "  핵심 개념을 설명한 답변입니다.  ",
                List.of("  개념을 정확히 설명했습니다.  "),
                List.of("  구체적인 예시를 추가하면 좋습니다.  "),
                FollowUpDecision.ASK,
                "  답변을 실제 사례와 연결할 필요가 있습니다.  ",
                "  gpt-5-mini  ",
                "  answer-analysis-v1  "
        );

        assertAll(
                () -> assertEquals(ANSWER_ID, analysis.getAnswerId()),
                () -> assertEquals("핵심 개념을 설명한 답변입니다.", analysis.getSummary()),
                () -> assertEquals(List.of("개념을 정확히 설명했습니다."), analysis.getStrengths()),
                () -> assertEquals(List.of("구체적인 예시를 추가하면 좋습니다."), analysis.getImprovements()),
                () -> assertEquals(FollowUpDecision.ASK, analysis.getFollowUpDecision()),
                () -> assertEquals("답변을 실제 사례와 연결할 필요가 있습니다.", analysis.getFollowUpReason()),
                () -> assertEquals("gpt-5-mini", analysis.getModel()),
                () -> assertEquals("answer-analysis-v1", analysis.getPromptVersion())
        );
    }

    @Test
    @DisplayName("잘한 점과 개선할 점은 빈 목록으로 생성할 수 있다")
    void allowsEmptyFeedbackItems() {
        AnswerAnalysis analysis = createAnalysis(List.of(), List.of());

        assertAll(
                () -> assertEquals(List.of(), analysis.getStrengths()),
                () -> assertEquals(List.of(), analysis.getImprovements())
        );
    }

    @Test
    @DisplayName("피드백 항목이 세 개를 초과하면 생성할 수 없다")
    void rejectsMoreThanThreeFeedbackItems() {
        List<String> tooManyItems = List.of("첫 번째", "두 번째", "세 번째", "네 번째");

        assertThrows(
                IllegalArgumentException.class,
                () -> createAnalysis(tooManyItems, List.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> createAnalysis(List.of(), tooManyItems)
        );
    }

    @Test
    @DisplayName("피드백 목록에 빈 내용이 포함되면 생성할 수 없다")
    void rejectsBlankFeedbackItem() {
        assertThrows(
                IllegalArgumentException.class,
                () -> createAnalysis(List.of(" "), List.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> createAnalysis(List.of(), List.of(" "))
        );
    }

    @Test
    @DisplayName("필수 분석 내용이 비어 있으면 생성할 수 없다")
    void rejectsBlankRequiredText() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AnswerAnalysis.create(
                        ANSWER_ID,
                        " ",
                        List.of(),
                        List.of(),
                        FollowUpDecision.SKIP,
                        "판단 근거",
                        "gpt-5-mini",
                        "answer-analysis-v1"
                )
        );
    }

    private static AnswerAnalysis createAnalysis(
            List<String> strengths,
            List<String> improvements
    ) {
        return AnswerAnalysis.create(
                ANSWER_ID,
                "답변 요약",
                strengths,
                improvements,
                FollowUpDecision.SKIP,
                "현재 답변만으로 충분합니다.",
                "gpt-5-mini",
                "answer-analysis-v1"
        );
    }
}
