package com.ward.techview.evaluation.api;

import com.ward.techview.evaluation.api.analysis.AnswerAnalysisResult;
import com.ward.techview.evaluation.api.analysis.FollowUpDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnswerAnalysisResultTest {

    @Test
    @DisplayName("꼬리 질문을 생성하는 답변 분석 결과를 생성한다")
    void createsAskResult() {
        AnswerAnalysisResult result = new AnswerAnalysisResult(
                "  핵심 개념을 설명한 답변입니다.  ",
                List.of("  핵심 용어를 정확히 사용했습니다.  "),
                List.of("  구체적인 사례가 부족합니다.  "),
                FollowUpDecision.ASK,
                "  실제 적용 경험을 확인할 필요가 있습니다.  ",
                "  실제 프로젝트에서는 어떻게 적용했나요?  ",
                "  gpt-5-mini  ",
                "  answer-analysis-v1  "
        );

        assertAll(
                () -> assertEquals("핵심 개념을 설명한 답변입니다.", result.summary()),
                () -> assertEquals(List.of("핵심 용어를 정확히 사용했습니다."), result.strengths()),
                () -> assertEquals(List.of("구체적인 사례가 부족합니다."), result.improvements()),
                () -> assertEquals(FollowUpDecision.ASK, result.followUpDecision()),
                () -> assertEquals("실제 적용 경험을 확인할 필요가 있습니다.", result.followUpReason()),
                () -> assertEquals("실제 프로젝트에서는 어떻게 적용했나요?", result.followUpQuestion()),
                () -> assertEquals("gpt-5-mini", result.model()),
                () -> assertEquals("answer-analysis-v1", result.promptVersion())
        );
    }

    @Test
    @DisplayName("꼬리 질문을 생략하는 답변 분석 결과를 생성한다")
    void createsSkipResult() {
        AnswerAnalysisResult result = createResult(
                List.of(),
                List.of(),
                FollowUpDecision.SKIP,
                null
        );

        assertAll(
                () -> assertEquals(FollowUpDecision.SKIP, result.followUpDecision()),
                () -> assertNull(result.followUpQuestion())
        );
    }

    @Test
    @DisplayName("ASK 결정에는 꼬리 질문이 필요하다")
    void rejectsAskWithoutFollowUpQuestion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> createResult(
                        List.of(),
                        List.of(),
                        FollowUpDecision.ASK,
                        null
                )
        );
    }

    @Test
    @DisplayName("SKIP 결정에는 꼬리 질문을 포함할 수 없다")
    void rejectsSkipWithFollowUpQuestion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> createResult(
                        List.of(),
                        List.of(),
                        FollowUpDecision.SKIP,
                        "불필요한 꼬리 질문"
                )
        );
    }

    @Test
    @DisplayName("피드백 항목이 세 개를 초과하면 생성할 수 없다")
    void rejectsMoreThanThreeFeedbackItems() {
        List<String> tooManyItems = List.of("첫 번째", "두 번째", "세 번째", "네 번째");

        assertThrows(
                IllegalArgumentException.class,
                () -> createResult(
                        tooManyItems,
                        List.of(),
                        FollowUpDecision.SKIP,
                        null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> createResult(
                        List.of(),
                        tooManyItems,
                        FollowUpDecision.SKIP,
                        null
                )
        );
    }

    @Test
    @DisplayName("피드백 목록에 빈 내용이 포함되면 생성할 수 없다")
    void rejectsBlankFeedbackItem() {
        assertThrows(
                IllegalArgumentException.class,
                () -> createResult(
                        List.of(" "),
                        List.of(),
                        FollowUpDecision.SKIP,
                        null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> createResult(
                        List.of(),
                        List.of(" "),
                        FollowUpDecision.SKIP,
                        null
                )
        );
    }

    private static AnswerAnalysisResult createResult(
            List<String> strengths,
            List<String> improvements,
            FollowUpDecision decision,
            String followUpQuestion
    ) {
        return new AnswerAnalysisResult(
                "답변 요약",
                strengths,
                improvements,
                decision,
                "꼬리 질문 판단 근거",
                followUpQuestion,
                "gpt-5-mini",
                "answer-analysis-v1"
        );
    }
}
