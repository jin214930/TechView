package com.ward.techview.evaluation.api;

import com.ward.techview.evaluation.api.feedback.InterviewFeedbackTurnPrompt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InterviewFeedbackTurnPromptTest {

    @Test
    @DisplayName("종합 피드백 턴 입력을 생성하고 문자열 앞뒤 공백을 제거한다")
    void createsTurnPrompt() {
        InterviewFeedbackTurnPrompt prompt = new InterviewFeedbackTurnPrompt(
                10L,
                1,
                "  JAVA  ",
                "  다형성을 설명해 주세요.  ",
                "  실제 사용 예시를 설명해 주세요.  ",
                "  전략 패턴에서 활용할 수 있습니다.  ",
                "  다형성의 활용 사례를 설명했습니다.  ",
                List.of("  구체적인 사례를 제시했습니다.  "),
                List.of("  원리를 더 설명하면 좋습니다.  ")
        );

        assertAll(
                () -> assertEquals(10L, prompt.answerId()),
                () -> assertEquals(1, prompt.turnOrder()),
                () -> assertEquals("JAVA", prompt.category()),
                () -> assertEquals("다형성을 설명해 주세요.", prompt.coreQuestion()),
                () -> assertEquals("실제 사용 예시를 설명해 주세요.", prompt.followUpQuestion()),
                () -> assertEquals("전략 패턴에서 활용할 수 있습니다.", prompt.transcript()),
                () -> assertEquals("다형성의 활용 사례를 설명했습니다.", prompt.analysisSummary()),
                () -> assertEquals(List.of("구체적인 사례를 제시했습니다."), prompt.analysisStrengths()),
                () -> assertEquals(List.of("원리를 더 설명하면 좋습니다."), prompt.analysisImprovements())
        );
    }

    @Test
    @DisplayName("답변 분석 강점과 개선점은 빈 목록일 수 있다")
    void allowsEmptyAnalysisFeedbackItems() {
        InterviewFeedbackTurnPrompt prompt = createPrompt(List.of(), List.of());

        assertAll(
                () -> assertEquals(List.of(), prompt.analysisStrengths()),
                () -> assertEquals(List.of(), prompt.analysisImprovements())
        );
    }

    @Test
    @DisplayName("답변 ID와 양수 턴 순서는 필수이다")
    void rejectsInvalidIdentityAndTurnOrder() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> createPrompt(null, 1, null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createPrompt(10L, 0, null)
                )
        );
    }

    @Test
    @DisplayName("필수 문자열이 비어 있으면 턴 입력을 생성할 수 없다")
    void rejectsBlankRequiredText() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new InterviewFeedbackTurnPrompt(
                                10L, 1, " ", "질문", null, "답변", "요약", List.of(), List.of()
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new InterviewFeedbackTurnPrompt(
                                10L, 1, "JAVA", "질문", " ", "답변", "요약", List.of(), List.of()
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new InterviewFeedbackTurnPrompt(
                                10L, 1, "JAVA", "질문", null, " ", "요약", List.of(), List.of()
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new InterviewFeedbackTurnPrompt(
                                10L, 1, "JAVA", "질문", null, "답변", " ", List.of(), List.of()
                        )
                )
        );
    }

    @Test
    @DisplayName("답변 분석 항목은 최대 세 개이며 빈 내용을 포함할 수 없다")
    void rejectsInvalidAnalysisFeedbackItems() {
        List<String> tooManyItems = List.of("첫 번째", "두 번째", "세 번째", "네 번째");

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createPrompt(tooManyItems, List.of())
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createPrompt(List.of(), tooManyItems)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> createPrompt(List.of(" "), List.of())
                )
        );
    }

    private static InterviewFeedbackTurnPrompt createPrompt(
            List<String> strengths,
            List<String> improvements
    ) {
        return new InterviewFeedbackTurnPrompt(
                10L,
                1,
                "JAVA",
                "다형성을 설명해 주세요.",
                null,
                "답변",
                "분석 요약",
                strengths,
                improvements
        );
    }

    private static InterviewFeedbackTurnPrompt createPrompt(
            Long answerId,
            int turnOrder,
            String followUpQuestion
    ) {
        return new InterviewFeedbackTurnPrompt(
                answerId,
                turnOrder,
                "JAVA",
                "다형성을 설명해 주세요.",
                followUpQuestion,
                "답변",
                "분석 요약",
                List.of(),
                List.of()
        );
    }
}
