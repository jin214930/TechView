package com.ward.techview.processing.service;

import com.ward.techview.processing.api.FinalFeedbackProcessingSnapshot;
import com.ward.techview.processing.api.FinalFeedbackProcessingStatus;
import com.ward.techview.processing.entity.FinalFeedbackProcessingJob;
import com.ward.techview.processing.entity.ProcessingJobStatus;
import com.ward.techview.processing.repository.FinalFeedbackProcessingJobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinalFeedbackProcessingQueryServiceTest {

    private static final Long SESSION_ID = 10L;

    @Mock
    private FinalFeedbackProcessingJobRepository jobRepository;

    @InjectMocks
    private FinalFeedbackProcessingQueryService service;

    @Test
    @DisplayName("종합 피드백 작업이 없으면 미시작 상태를 반환한다")
    void returnsNotStartedWhenJobDoesNotExist() {
        when(jobRepository.findByInterviewSessionId(SESSION_ID))
                .thenReturn(Optional.empty());

        FinalFeedbackProcessingSnapshot snapshot = service.getStatus(SESSION_ID);

        assertEquals(FinalFeedbackProcessingStatus.NOT_STARTED, snapshot.status());
    }

    @ParameterizedTest
    @EnumSource(
            value = ProcessingJobStatus.class,
            names = {"QUEUED", "PROCESSING", "RETRY_WAIT"}
    )
    @DisplayName("대기·처리·재시도 작업은 처리 중 상태를 반환한다")
    void returnsProcessingWhenJobIsPending(ProcessingJobStatus jobStatus) {
        prepareJob(jobStatus);

        FinalFeedbackProcessingSnapshot snapshot = service.getStatus(SESSION_ID);

        assertEquals(FinalFeedbackProcessingStatus.PROCESSING, snapshot.status());
    }

    @Test
    @DisplayName("성공한 작업은 성공 상태를 반환한다")
    void returnsSucceededWhenJobSucceeded() {
        prepareJob(ProcessingJobStatus.SUCCEEDED);

        FinalFeedbackProcessingSnapshot snapshot = service.getStatus(SESSION_ID);

        assertEquals(FinalFeedbackProcessingStatus.SUCCEEDED, snapshot.status());
    }

    @Test
    @DisplayName("최종 실패한 작업은 실패 상태를 반환한다")
    void returnsFailedWhenJobFailed() {
        prepareJob(ProcessingJobStatus.FAILED);

        FinalFeedbackProcessingSnapshot snapshot = service.getStatus(SESSION_ID);

        assertEquals(FinalFeedbackProcessingStatus.FAILED, snapshot.status());
    }

    @Test
    @DisplayName("면접 세션 ID가 없으면 작업 저장소를 조회하지 않는다")
    void rejectsNullInterviewSessionId() {
        assertThrows(NullPointerException.class, () -> service.getStatus(null));

        verifyNoInteractions(jobRepository);
    }

    private void prepareJob(ProcessingJobStatus status) {
        when(jobRepository.findByInterviewSessionId(SESSION_ID))
                .thenReturn(Optional.of(job(status)));
    }

    private static FinalFeedbackProcessingJob job(ProcessingJobStatus status) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        FinalFeedbackProcessingJob job = FinalFeedbackProcessingJob.create(
                SESSION_ID,
                now.minusMinutes(1)
        );

        if (status == ProcessingJobStatus.QUEUED) {
            return job;
        }

        job.claim(now, Duration.ofMinutes(1));

        switch (status) {
            case PROCESSING -> {
            }
            case RETRY_WAIT -> job.retry(
                    now.plusMinutes(1),
                    "TEMPORARY_FAILURE",
                    "일시적인 오류"
            );
            case SUCCEEDED -> job.succeed();
            case FAILED -> job.fail(
                    "FINAL_FEEDBACK_FAILED",
                    "종합 피드백 생성에 실패했습니다."
            );
            case QUEUED -> throw new IllegalStateException("이미 처리한 상태입니다.");
        }

        return job;
    }
}
