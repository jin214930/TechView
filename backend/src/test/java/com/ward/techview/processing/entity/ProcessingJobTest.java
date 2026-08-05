package com.ward.techview.processing.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingJobTest {

    private static final Long SESSION_ID = 1L;
    private static final Long ANSWER_ID = 10L;
    private static final String AUDIO_OBJECT_KEY = "interviews/1/answers/10/audio.webm";
    private static final String SOURCE_OBJECT_ETAG = "verified-etag";
    private static final OffsetDateTime QUEUED_AT = OffsetDateTime.of(
            2026,
            8,
            3,
            12,
            0,
            0,
            0,
            ZoneOffset.UTC
    );
    private static final Duration LEASE_DURATION = Duration.ofMinutes(1);

    @Test
    @DisplayName("작업을 생성하면 대기 상태와 기본 시도 횟수가 설정된다")
    void createsQueuedJob() {
        SttProcessingJob job = createJob(QUEUED_AT);

        assertAll(
                () -> assertEquals(ProcessingJobStatus.QUEUED, job.getStatus()),
                () -> assertEquals(0, job.getAttemptCount()),
                () -> assertEquals(3, job.getMaxAttempts()),
                () -> assertEquals(QUEUED_AT, job.getNextAttemptAt()),
                () -> assertNull(job.getLeaseExpiresAt()),
                () -> assertNull(job.getErrorCode()),
                () -> assertNull(job.getErrorMessage())
        );
    }

    @Test
    @DisplayName("실행 시각이 도래한 대기 작업을 선점한다")
    void claimsQueuedJob() {
        SttProcessingJob job = createJob(QUEUED_AT);

        job.claim(QUEUED_AT, LEASE_DURATION);

        assertAll(
                () -> assertEquals(ProcessingJobStatus.PROCESSING, job.getStatus()),
                () -> assertEquals(1, job.getAttemptCount()),
                () -> assertNull(job.getNextAttemptAt()),
                () -> assertEquals(QUEUED_AT.plus(LEASE_DURATION), job.getLeaseExpiresAt())
        );
    }

    @Test
    @DisplayName("실행 예정 시각 전에는 대기 작업을 선점할 수 없다")
    void rejectsClaimBeforeScheduledTime() {
        SttProcessingJob job = createJob(QUEUED_AT.plusSeconds(1));

        assertFalse(job.isClaimable(QUEUED_AT));
        assertThrows(
                IllegalStateException.class,
                () -> job.claim(QUEUED_AT, LEASE_DURATION)
        );
    }

    @Test
    @DisplayName("lease가 유효한 처리 중 작업은 다시 선점할 수 없다")
    void rejectsClaimWhileLeaseIsActive() {
        SttProcessingJob job = createJob(QUEUED_AT);
        job.claim(QUEUED_AT, LEASE_DURATION);

        OffsetDateTime beforeLeaseExpiration = QUEUED_AT.plus(LEASE_DURATION).minusNanos(1);

        assertFalse(job.isClaimable(beforeLeaseExpiration));
        assertThrows(
                IllegalStateException.class,
                () -> job.claim(beforeLeaseExpiration, LEASE_DURATION)
        );
    }

    @Test
    @DisplayName("lease가 만료된 처리 중 작업은 다시 선점할 수 있다")
    void reclaimsProcessingJobAfterLeaseExpiration() {
        SttProcessingJob job = createJob(QUEUED_AT);
        job.claim(QUEUED_AT, LEASE_DURATION);
        OffsetDateTime leaseExpiredAt = QUEUED_AT.plus(LEASE_DURATION);

        job.claim(leaseExpiredAt, LEASE_DURATION);

        assertAll(
                () -> assertEquals(ProcessingJobStatus.PROCESSING, job.getStatus()),
                () -> assertEquals(2, job.getAttemptCount()),
                () -> assertEquals(leaseExpiredAt.plus(LEASE_DURATION), job.getLeaseExpiresAt())
        );
    }

    @Test
    @DisplayName("처리 중 작업을 성공 상태로 변경한다")
    void movesProcessingJobToSucceeded() {
        SttProcessingJob job = createJob(QUEUED_AT);
        job.claim(QUEUED_AT, LEASE_DURATION);

        job.succeed();

        assertAll(
                () -> assertEquals(ProcessingJobStatus.SUCCEEDED, job.getStatus()),
                () -> assertNull(job.getNextAttemptAt()),
                () -> assertNull(job.getLeaseExpiresAt()),
                () -> assertNull(job.getErrorCode()),
                () -> assertNull(job.getErrorMessage()),
                () -> assertFalse(job.isClaimable(QUEUED_AT.plusHours(1)))
        );
    }

    @Test
    @DisplayName("처리 중 작업에 재시도 시각과 실패 정보를 기록한다")
    void movesProcessingJobToRetryWait() {
        SttProcessingJob job = createJob(QUEUED_AT);
        job.claim(QUEUED_AT, LEASE_DURATION);
        OffsetDateTime nextAttemptAt = QUEUED_AT.plusMinutes(5);

        job.retry(nextAttemptAt, "STT_TIMEOUT", "STT 요청 시간이 초과되었습니다.");

        assertAll(
                () -> assertEquals(ProcessingJobStatus.RETRY_WAIT, job.getStatus()),
                () -> assertEquals(nextAttemptAt, job.getNextAttemptAt()),
                () -> assertNull(job.getLeaseExpiresAt()),
                () -> assertEquals("STT_TIMEOUT", job.getErrorCode()),
                () -> assertEquals("STT 요청 시간이 초과되었습니다.", job.getErrorMessage()),
                () -> assertFalse(job.isClaimable(nextAttemptAt.minusNanos(1))),
                () -> assertTrue(job.isClaimable(nextAttemptAt))
        );
    }

    @Test
    @DisplayName("최대 시도 횟수에 도달하면 재시도 요청을 최종 실패로 변경한다")
    void movesJobToFailedWhenMaxAttemptsReached() {
        SttProcessingJob job = createJob(QUEUED_AT);
        OffsetDateTime firstRetryAt = QUEUED_AT.plusMinutes(1);
        OffsetDateTime secondRetryAt = QUEUED_AT.plusMinutes(2);

        job.claim(QUEUED_AT, LEASE_DURATION);
        job.retry(firstRetryAt, "STT_TIMEOUT", "첫 번째 실패");
        job.claim(firstRetryAt, LEASE_DURATION);
        job.retry(secondRetryAt, "STT_TIMEOUT", "두 번째 실패");
        job.claim(secondRetryAt, LEASE_DURATION);
        job.retry(secondRetryAt.plusMinutes(1), "STT_TIMEOUT", "세 번째 실패");

        assertAll(
                () -> assertEquals(ProcessingJobStatus.FAILED, job.getStatus()),
                () -> assertEquals(3, job.getAttemptCount()),
                () -> assertNull(job.getNextAttemptAt()),
                () -> assertNull(job.getLeaseExpiresAt()),
                () -> assertEquals("STT_TIMEOUT", job.getErrorCode()),
                () -> assertEquals("세 번째 실패", job.getErrorMessage()),
                () -> assertFalse(job.isClaimable(secondRetryAt.plusHours(1)))
        );
    }

    @Test
    @DisplayName("처리 중 작업을 즉시 최종 실패 상태로 변경한다")
    void movesProcessingJobToFailed() {
        SttProcessingJob job = createJob(QUEUED_AT);
        job.claim(QUEUED_AT, LEASE_DURATION);

        job.fail("INVALID_AUDIO", "지원하지 않는 음성 파일입니다.");

        assertAll(
                () -> assertEquals(ProcessingJobStatus.FAILED, job.getStatus()),
                () -> assertNull(job.getNextAttemptAt()),
                () -> assertNull(job.getLeaseExpiresAt()),
                () -> assertEquals("INVALID_AUDIO", job.getErrorCode()),
                () -> assertEquals("지원하지 않는 음성 파일입니다.", job.getErrorMessage())
        );
    }

    @Test
    @DisplayName("실패한 STT 작업은 실패 시각 24시간 후로 음성 정리를 예약한다")
    void schedulesAudioCleanupAfterFailure() {
        SttProcessingJob job = createJob(QUEUED_AT);
        job.claim(QUEUED_AT, LEASE_DURATION);
        job.fail("INVALID_AUDIO", "지원하지 않는 음성 파일입니다.");

        job.scheduleAudioCleanup(QUEUED_AT);

        assertEquals(
                QUEUED_AT.plusHours(24),
                job.getAudioCleanupDueAt()
        );
    }

    @Test
    @DisplayName("실패하지 않은 STT 작업은 음성 정리를 예약할 수 없다")
    void rejectsAudioCleanupScheduleForNonFailedJob() {
        SttProcessingJob job = createJob(QUEUED_AT);

        assertThrows(
                IllegalStateException.class,
                () -> job.scheduleAudioCleanup(QUEUED_AT)
        );
        assertNull(job.getAudioCleanupDueAt());
    }

    @Test
    @DisplayName("정리 예정 시각이 지난 실패 음성을 삭제 완료 처리한다")
    void marksFailedAudioAsDeleted() {
        SttProcessingJob job = createJob(QUEUED_AT);
        job.claim(QUEUED_AT, LEASE_DURATION);
        job.fail("INVALID_AUDIO", "지원하지 않는 음성 파일입니다.");
        job.scheduleAudioCleanup(QUEUED_AT);
        OffsetDateTime deletedAt = QUEUED_AT.plusHours(25);

        job.markAudioDeleted(deletedAt);
        job.markAudioDeleted(deletedAt.plusMinutes(1));

        assertEquals(deletedAt, job.getAudioDeletedAt());
    }

    @Test
    @DisplayName("정리 예정 시각 전에는 실패 음성을 삭제 완료 처리할 수 없다")
    void rejectsAudioDeletionBeforeCleanupDueTime() {
        SttProcessingJob job = createJob(QUEUED_AT);
        job.claim(QUEUED_AT, LEASE_DURATION);
        job.fail("INVALID_AUDIO", "지원하지 않는 음성 파일입니다.");
        job.scheduleAudioCleanup(QUEUED_AT);

        assertThrows(
                IllegalArgumentException.class,
                () -> job.markAudioDeleted(
                        QUEUED_AT.plusHours(24).minusNanos(1)
                )
        );
        assertNull(job.getAudioDeletedAt());
    }

    @Test
    @DisplayName("잘못된 재시도 정보는 예외를 발생시키고 처리 상태를 유지한다")
    void rejectsInvalidRetryWithoutChangingState() {
        SttProcessingJob job = createJob(QUEUED_AT);
        job.claim(QUEUED_AT, LEASE_DURATION);
        OffsetDateTime leaseExpiresAt = job.getLeaseExpiresAt();

        assertThrows(
                IllegalArgumentException.class,
                () -> job.retry(QUEUED_AT.plusMinutes(1), " ", "일시적 실패")
        );
        assertAll(
                () -> assertEquals(ProcessingJobStatus.PROCESSING, job.getStatus()),
                () -> assertEquals(leaseExpiresAt, job.getLeaseExpiresAt()),
                () -> assertNull(job.getNextAttemptAt()),
                () -> assertNull(job.getErrorCode()),
                () -> assertNull(job.getErrorMessage())
        );
    }

    @Test
    @DisplayName("처리 중이 아닌 작업은 성공이나 실패 상태로 변경할 수 없다")
    void rejectsCompletionWhenJobIsNotProcessing() {
        SttProcessingJob job = createJob(QUEUED_AT);

        assertAll(
                () -> assertThrows(IllegalStateException.class, job::succeed),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> job.fail("STT_ERROR", "STT 처리 실패")
                )
        );
    }

    @Test
    @DisplayName("처리 중인 작업의 현재 시도 번호가 일치하는지 확인한다")
    void recognizesCurrentProcessingAttempt() {
        SttProcessingJob job = createJob(QUEUED_AT);
        job.claim(QUEUED_AT, LEASE_DURATION);

        assertAll(
                () -> assertTrue(job.isCurrentAttempt(1)),
                () -> assertFalse(job.isCurrentAttempt(2))
        );
    }

    @Test
    @DisplayName("처리 중이 아닌 작업은 시도 번호가 같아도 현재 시도로 판단하지 않는다")
    void rejectsAttemptWhenJobIsNotProcessing() {
        SttProcessingJob job = createJob(QUEUED_AT);

        assertFalse(job.isCurrentAttempt(0));

        job.claim(QUEUED_AT, LEASE_DURATION);
        job.succeed();

        assertFalse(job.isCurrentAttempt(1));
    }

    @Test
    @DisplayName("처리 lease가 남아 있는 작업은 현재 실행 중인 것으로 판단한다")
    void recognizesActivelyProcessingJob() {
        SttProcessingJob job = createJob(QUEUED_AT);
        job.claim(QUEUED_AT, LEASE_DURATION);

        assertTrue(job.isActivelyProcessing(QUEUED_AT.plusSeconds(30)));
    }

    @Test
    @DisplayName("처리 lease가 만료됐거나 처리 중 상태가 아니면 현재 실행 중이 아니다")
    void rejectsInactiveOrExpiredProcessingJob() {
        SttProcessingJob queuedJob = createJob(QUEUED_AT);
        SttProcessingJob expiredJob = createJob(QUEUED_AT);
        expiredJob.claim(QUEUED_AT, LEASE_DURATION);

        assertAll(
                () -> assertFalse(queuedJob.isActivelyProcessing(QUEUED_AT)),
                () -> assertFalse(expiredJob.isActivelyProcessing(
                        QUEUED_AT.plus(LEASE_DURATION)
                )),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> expiredJob.isActivelyProcessing(null)
                )
        );
    }

    private static SttProcessingJob createJob(OffsetDateTime queuedAt) {
        return SttProcessingJob.create(
                SESSION_ID,
                ANSWER_ID,
                AUDIO_OBJECT_KEY,
                SOURCE_OBJECT_ETAG,
                queuedAt
        );
    }
}
