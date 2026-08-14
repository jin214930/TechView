package com.ward.techview.evaluation.api;

import com.ward.techview.evaluation.api.feedback.InterviewFeedbackPrompt;
import com.ward.techview.evaluation.api.feedback.InterviewFeedbackTurnPrompt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InterviewFeedbackPromptTest {

    @Test
    @DisplayName("면접 턴 목록을 복사하여 종합 피드백 입력을 생성한다")
    void createsPromptWithCopiedTurns() {
        List<InterviewFeedbackTurnPrompt> source = new ArrayList<>();
        source.add(turn(1, 10L));

        InterviewFeedbackPrompt prompt = new InterviewFeedbackPrompt(1L, source);
        source.add(turn(2, 20L));

        assertEquals(List.of(turn(1, 10L)), prompt.turns());
    }

    @Test
    @DisplayName("면접 세션 ID와 턴 목록은 필수이다")
    void rejectsNullRequiredValues() {
        assertThrows(
                NullPointerException.class,
                () -> new InterviewFeedbackPrompt(null, List.of(turn(1, 10L)))
        );
        assertThrows(
                NullPointerException.class,
                () -> new InterviewFeedbackPrompt(1L, null)
        );
    }

    @Test
    @DisplayName("면접 턴 목록은 비어 있거나 null을 포함할 수 없다")
    void rejectsInvalidTurns() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new InterviewFeedbackPrompt(1L, List.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new InterviewFeedbackPrompt(
                        1L,
                        Arrays.asList(turn(1, 10L), null)
                )
        );
    }

    @Test
    @DisplayName("면접 턴 순서는 중복될 수 없다")
    void rejectsDuplicateTurnOrder() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new InterviewFeedbackPrompt(
                        1L,
                        List.of(turn(1, 10L), turn(1, 20L))
                )
        );
    }

    private static InterviewFeedbackTurnPrompt turn(int turnOrder, Long answerId) {
        return new InterviewFeedbackTurnPrompt(
                answerId,
                turnOrder,
                "JAVA",
                "핵심 질문",
                null,
                "답변",
                "분석 요약",
                List.of(),
                List.of()
        );
    }
}
