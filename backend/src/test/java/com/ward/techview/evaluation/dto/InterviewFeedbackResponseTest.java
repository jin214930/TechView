package com.ward.techview.evaluation.dto;

import com.ward.techview.evaluation.api.feedback.CategoryFeedback;
import com.ward.techview.evaluation.entity.InterviewFeedback;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InterviewFeedbackResponseTest {

    private static final Long SESSION_ID = 10L;

    @Test
    @DisplayName("처리 중인 종합 피드백 응답을 생성한다")
    void createsProcessingResponse() {
        InterviewFeedbackResponse response =
                InterviewFeedbackResponse.processing(SESSION_ID);

        assertAll(
                () -> assertEquals(SESSION_ID, response.sessionId()),
                () -> assertEquals(InterviewFeedbackStatus.PROCESSING, response.status()),
                () -> assertNull(response.feedback()),
                () -> assertNull(response.errorMessage())
        );
    }

    @Test
    @DisplayName("최종 실패한 종합 피드백 응답에는 일반화된 오류 메시지만 포함한다")
    void createsFailedResponseWithGenericMessage() {
        InterviewFeedbackResponse response =
                InterviewFeedbackResponse.failed(SESSION_ID);

        assertAll(
                () -> assertEquals(InterviewFeedbackStatus.FAILED, response.status()),
                () -> assertNull(response.feedback()),
                () -> assertEquals(
                        "피드백을 제공할 수 없습니다.",
                        response.errorMessage()
                )
        );
    }

    @Test
    @DisplayName("완료된 종합 피드백 엔티티를 상세 응답으로 변환한다")
    void createsCompletedResponseFromFeedback() {
        InterviewFeedbackResponse response =
                InterviewFeedbackResponse.completed(feedback());

        InterviewFeedbackDetailResponse detail = response.feedback();
        assertAll(
                () -> assertEquals(SESSION_ID, response.sessionId()),
                () -> assertEquals(InterviewFeedbackStatus.COMPLETED, response.status()),
                () -> assertNull(response.errorMessage()),
                () -> assertEquals("전체 총평", detail.overallSummary()),
                () -> assertEquals(List.of("강점"), detail.strengths()),
                () -> assertEquals(List.of("개선점"), detail.improvements()),
                () -> assertEquals(5, detail.categoryFeedbacks().size()),
                () -> assertEquals("JAVA", detail.categoryFeedbacks().get(0).category()),
                () -> assertEquals("평가", detail.categoryFeedbacks().get(0).assessment()),
                () -> assertEquals("추천", detail.categoryFeedbacks().get(0).recommendation()),
                () -> assertEquals(List.of("추천 주제"), detail.recommendedTopics())
        );
    }

    private static InterviewFeedback feedback() {
        return InterviewFeedback.create(
                SESSION_ID,
                "전체 총평",
                List.of("강점"),
                List.of("개선점"),
                List.of(
                        categoryFeedback("JAVA"),
                        categoryFeedback("SPRING"),
                        categoryFeedback("DB"),
                        categoryFeedback("OS"),
                        categoryFeedback("NETWORK")
                ),
                List.of("추천 주제"),
                "model",
                "v1"
        );
    }

    private static CategoryFeedback categoryFeedback(String category) {
        return new CategoryFeedback(category, "평가", "추천");
    }
}
