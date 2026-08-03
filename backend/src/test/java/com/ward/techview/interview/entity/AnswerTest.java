package com.ward.techview.interview.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerTest {

    private static final OffsetDateTime TRANSCRIBED_AT = OffsetDateTime.of(
            2026,
            8,
            3,
            12,
            0,
            0,
            0,
            ZoneOffset.UTC
    );

    @Test
    @DisplayName("답변의 전사문과 전사 완료 시각을 저장한다")
    void completesTranscription() {
        Answer answer = createAnswer();

        answer.completeTranscription("의존성 주입은 객체의 의존 관계를 외부에서 제공하는 방식입니다.", TRANSCRIBED_AT);

        assertAll(
                () -> assertTrue(answer.isTranscribed()),
                () -> assertEquals(
                        "의존성 주입은 객체의 의존 관계를 외부에서 제공하는 방식입니다.",
                        answer.getTranscript()
                ),
                () -> assertEquals(TRANSCRIBED_AT, answer.getTranscribedAt())
        );
    }

    @Test
    @DisplayName("빈 전사문은 저장하지 않는다")
    void rejectsBlankTranscript() {
        Answer answer = createAnswer();

        assertThrows(
                IllegalArgumentException.class,
                () -> answer.completeTranscription(" ", TRANSCRIBED_AT)
        );
        assertAll(
                () -> assertFalse(answer.isTranscribed()),
                () -> assertNull(answer.getTranscript()),
                () -> assertNull(answer.getTranscribedAt())
        );
    }

    @Test
    @DisplayName("전사 완료 시각이 없으면 전사 결과를 저장하지 않는다")
    void rejectsNullTranscribedAt() {
        Answer answer = createAnswer();

        assertThrows(
                NullPointerException.class,
                () -> answer.completeTranscription("전사 결과", null)
        );
        assertAll(
                () -> assertFalse(answer.isTranscribed()),
                () -> assertNull(answer.getTranscript()),
                () -> assertNull(answer.getTranscribedAt())
        );
    }

    @Test
    @DisplayName("이미 전사가 완료된 답변은 최초 전사 결과를 유지한다")
    void preservesFirstTranscription() {
        Answer answer = createAnswer();
        answer.completeTranscription("최초 전사 결과", TRANSCRIBED_AT);

        answer.completeTranscription(
                "재시도로 생성된 전사 결과",
                TRANSCRIBED_AT.plusMinutes(1)
        );

        assertAll(
                () -> assertTrue(answer.isTranscribed()),
                () -> assertEquals("최초 전사 결과", answer.getTranscript()),
                () -> assertEquals(TRANSCRIBED_AT, answer.getTranscribedAt())
        );
    }

    private static Answer createAnswer() {
        InterviewSession session = InterviewSession.create(1L, TRANSCRIBED_AT.minusMinutes(1));
        InterviewCoreQuestion coreQuestion = InterviewCoreQuestion.create(
                session,
                10L,
                "의존성 주입에 대해 설명해 주세요.",
                "SPRING",
                1
        );
        InterviewTurn turn = InterviewTurn.createCore(session, 1, coreQuestion);

        return Answer.create(
                turn,
                "interviews/1/answers/audio.webm",
                "audio/webm",
                30_000
        );
    }
}
