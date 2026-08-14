package com.ward.techview.processing.service;

import com.ward.techview.processing.api.analysis.AnswerProcessingSnapshot;
import com.ward.techview.processing.api.analysis.AnswerProcessingStatus;
import com.ward.techview.processing.entity.AnswerAnalysisProcessingJob;
import com.ward.techview.processing.entity.ProcessingJobStatus;
import com.ward.techview.processing.entity.SttProcessingJob;
import com.ward.techview.processing.repository.AnswerAnalysisProcessingJobRepository;
import com.ward.techview.processing.repository.SttProcessingJobRepository;
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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerProcessingQueryServiceTest {

    private static final Long ANSWER_ID = 20L;
    private static final String FAILURE_CODE = "ANSWER_PROCESSING_FAILED";

    @Mock
    private SttProcessingJobRepository sttJobRepository;

    @Mock
    private AnswerAnalysisProcessingJobRepository analysisJobRepository;

    @InjectMocks
    private AnswerProcessingQueryService service;

    @Test
    @DisplayName("처리 작업이 없으면 미시작 상태를 반환한다")
    void returnsNotStartedWhenNoJobExists() {
        when(analysisJobRepository.findByAnswerId(ANSWER_ID))
                .thenReturn(Optional.empty());
        when(sttJobRepository.findByAnswerId(ANSWER_ID))
                .thenReturn(Optional.empty());

        AnswerProcessingSnapshot result = service.getStatus(ANSWER_ID);

        assertSnapshot(result, AnswerProcessingStatus.NOT_STARTED, null);
    }

    @ParameterizedTest
    @EnumSource(
            value = ProcessingJobStatus.class,
            names = {"QUEUED", "PROCESSING", "RETRY_WAIT"}
    )
    @DisplayName("답변 분석 작업이 대기 또는 처리 중이면 처리 중 상태를 반환한다")
    void returnsProcessingWhenAnalysisJobIsPending(ProcessingJobStatus status) {
        AnswerAnalysisProcessingJob job = analysisJob(status);
        when(analysisJobRepository.findByAnswerId(ANSWER_ID))
                .thenReturn(Optional.of(job));

        AnswerProcessingSnapshot result = service.getStatus(ANSWER_ID);

        assertSnapshot(result, AnswerProcessingStatus.PROCESSING, null);
        verifyNoInteractions(sttJobRepository);
    }

    @Test
    @DisplayName("답변 분석 작업이 성공했으면 완료 상태를 반환한다")
    void returnsCompletedWhenAnalysisJobSucceeded() {
        AnswerAnalysisProcessingJob job = analysisJob(ProcessingJobStatus.SUCCEEDED);
        when(analysisJobRepository.findByAnswerId(ANSWER_ID))
                .thenReturn(Optional.of(job));

        AnswerProcessingSnapshot result = service.getStatus(ANSWER_ID);

        assertSnapshot(result, AnswerProcessingStatus.COMPLETED, null);
        verifyNoInteractions(sttJobRepository);
    }

    @Test
    @DisplayName("답변 분석 작업이 실패했으면 실패 코드와 함께 실패 상태를 반환한다")
    void returnsFailedWhenAnalysisJobFailed() {
        AnswerAnalysisProcessingJob job = analysisJob(ProcessingJobStatus.FAILED);
        when(analysisJobRepository.findByAnswerId(ANSWER_ID))
                .thenReturn(Optional.of(job));

        AnswerProcessingSnapshot result = service.getStatus(ANSWER_ID);

        assertSnapshot(result, AnswerProcessingStatus.FAILED, FAILURE_CODE);
        verifyNoInteractions(sttJobRepository);
    }

    @Test
    @DisplayName("분석 작업이 존재하면 STT 정리 실패보다 분석 상태를 우선한다")
    void prioritizesAnalysisJobOverSttCleanupFailure() {
        AnswerAnalysisProcessingJob analysisJob = analysisJob(ProcessingJobStatus.PROCESSING);
        when(analysisJobRepository.findByAnswerId(ANSWER_ID))
                .thenReturn(Optional.of(analysisJob));

        AnswerProcessingSnapshot result = service.getStatus(ANSWER_ID);

        assertSnapshot(result, AnswerProcessingStatus.PROCESSING, null);
        verify(sttJobRepository, never()).findByAnswerId(ANSWER_ID);
    }

    @ParameterizedTest
    @EnumSource(
            value = ProcessingJobStatus.class,
            names = {"QUEUED", "PROCESSING", "RETRY_WAIT", "SUCCEEDED"}
    )
    @DisplayName("분석 작업 없이 STT 작업이 존재하면 처리 중 상태를 반환한다")
    void returnsProcessingWhenOnlySttJobExists(ProcessingJobStatus status) {
        SttProcessingJob job = sttJob(status);
        when(analysisJobRepository.findByAnswerId(ANSWER_ID))
                .thenReturn(Optional.empty());
        when(sttJobRepository.findByAnswerId(ANSWER_ID))
                .thenReturn(Optional.of(job));

        AnswerProcessingSnapshot result = service.getStatus(ANSWER_ID);

        assertSnapshot(result, AnswerProcessingStatus.PROCESSING, null);
    }

    @Test
    @DisplayName("분석 작업 없이 STT 작업이 실패했으면 실패 상태를 반환한다")
    void returnsFailedWhenOnlySttJobFailed() {
        SttProcessingJob job = sttJob(ProcessingJobStatus.FAILED);
        when(analysisJobRepository.findByAnswerId(ANSWER_ID))
                .thenReturn(Optional.empty());
        when(sttJobRepository.findByAnswerId(ANSWER_ID))
                .thenReturn(Optional.of(job));

        AnswerProcessingSnapshot result = service.getStatus(ANSWER_ID);

        assertSnapshot(result, AnswerProcessingStatus.FAILED, FAILURE_CODE);
    }

    @Test
    @DisplayName("답변 ID가 없으면 작업 저장소를 조회하지 않는다")
    void rejectsNullAnswerId() {
        assertThrows(NullPointerException.class, () -> service.getStatus(null));

        verifyNoInteractions(analysisJobRepository, sttJobRepository);
    }

    private static AnswerAnalysisProcessingJob analysisJob(ProcessingJobStatus status) {
        AnswerAnalysisProcessingJob job = AnswerAnalysisProcessingJob.create(
                10L,
                ANSWER_ID,
                now().minusMinutes(1)
        );
        moveTo(job, status);
        return job;
    }

    private static SttProcessingJob sttJob(ProcessingJobStatus status) {
        SttProcessingJob job = SttProcessingJob.create(
                10L,
                ANSWER_ID,
                "interviews/10/answers/20/audio.webm",
                "verified-etag",
                now().minusMinutes(1)
        );
        moveTo(job, status);
        return job;
    }

    private static void moveTo(
            com.ward.techview.processing.entity.ProcessingJob job,
            ProcessingJobStatus status
    ) {
        if (status == ProcessingJobStatus.QUEUED) {
            return;
        }

        OffsetDateTime now = now();
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
            case FAILED -> job.fail(FAILURE_CODE, "처리에 실패했습니다.");
            case QUEUED -> throw new IllegalStateException("이미 처리한 상태입니다.");
        }
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private static void assertSnapshot(
            AnswerProcessingSnapshot snapshot,
            AnswerProcessingStatus status,
            String errorCode
    ) {
        assertAll(
                () -> assertEquals(status, snapshot.status()),
                () -> {
                    if (errorCode == null) {
                        assertNull(snapshot.errorCode());
                    } else {
                        assertEquals(errorCode, snapshot.errorCode());
                    }
                }
        );
    }
}
