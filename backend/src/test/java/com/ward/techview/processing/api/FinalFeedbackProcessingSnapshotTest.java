package com.ward.techview.processing.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinalFeedbackProcessingSnapshotTest {

    @Test
    @DisplayName("종합 피드백 처리 상태를 보관한다")
    void storesProcessingStatus() {
        FinalFeedbackProcessingSnapshot snapshot =
                new FinalFeedbackProcessingSnapshot(FinalFeedbackProcessingStatus.PROCESSING);

        assertEquals(FinalFeedbackProcessingStatus.PROCESSING, snapshot.status());
    }

    @Test
    @DisplayName("종합 피드백 처리 상태가 없으면 생성을 거부한다")
    void rejectsNullStatus() {
        assertThrows(
                NullPointerException.class,
                () -> new FinalFeedbackProcessingSnapshot(null)
        );
    }
}
