package com.ward.techview.evaluation.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerAnalysisPromptTest {

    @Test
    @DisplayName("핵심 질문 답변 분석 입력을 생성하고 문자열 양끝 공백을 제거한다")
    void createsCoreAnswerPrompt() {
        AnswerAnalysisPrompt prompt = new AnswerAnalysisPrompt(
                "  SPRING  ",
                "  의존성 주입에 대해 설명해 주세요.  ",
                null,
                "  객체의 의존 관계를 외부에서 제공하는 방식입니다.  ",
                true
        );

        assertAll(
                () -> assertEquals("SPRING", prompt.category()),
                () -> assertEquals("의존성 주입에 대해 설명해 주세요.", prompt.coreQuestion()),
                () -> assertNull(prompt.followUpQuestion()),
                () -> assertEquals(
                        "객체의 의존 관계를 외부에서 제공하는 방식입니다.",
                        prompt.transcript()
                ),
                () -> assertTrue(prompt.followUpAllowed())
        );
    }

    @Test
    @DisplayName("원본 핵심 질문을 포함한 꼬리 질문 답변 분석 입력을 생성한다")
    void createsFollowUpAnswerPrompt() {
        AnswerAnalysisPrompt prompt = new AnswerAnalysisPrompt(
                "SPRING",
                "의존성 주입에 대해 설명해 주세요.",
                "  생성자 주입의 장점은 무엇인가요?  ",
                "불변성과 테스트 용이성을 확보할 수 있습니다.",
                false
        );

        assertAll(
                () -> assertEquals("생성자 주입의 장점은 무엇인가요?", prompt.followUpQuestion()),
                () -> assertFalse(prompt.followUpAllowed())
        );
    }

    @Test
    @DisplayName("꼬리 질문 생성이 가능한 입력에는 기존 꼬리 질문을 포함할 수 없다")
    void rejectsFollowUpQuestionWhenFollowUpIsAllowed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnswerAnalysisPrompt(
                        "SPRING",
                        "핵심 질문",
                        "기존 꼬리 질문",
                        "답변 전사문",
                        true
                )
        );
    }

    @Test
    @DisplayName("꼬리 질문 답변 분석에는 꼬리 질문이 필요하다")
    void rejectsMissingFollowUpQuestionWhenFollowUpIsNotAllowed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnswerAnalysisPrompt(
                        "SPRING",
                        "핵심 질문",
                        null,
                        "답변 전사문",
                        false
                )
        );
    }

    @Test
    @DisplayName("필수 분석 입력이 비어 있으면 생성할 수 없다")
    void rejectsBlankRequiredInput() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnswerAnalysisPrompt(
                        " ",
                        "핵심 질문",
                        null,
                        "답변 전사문",
                        true
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnswerAnalysisPrompt(
                        "SPRING",
                        " ",
                        null,
                        "답변 전사문",
                        true
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnswerAnalysisPrompt(
                        "SPRING",
                        "핵심 질문",
                        null,
                        " ",
                        true
                )
        );
    }
}
