ALTER TABLE stt_processing_job
    ADD COLUMN audio_cleanup_due_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN audio_deleted_at TIMESTAMP WITH TIME ZONE;

-- 기존 실패 작업도 실패 처리 시각(updated_at)으로부터 24시간 뒤 정리 대상에 포함한다.
UPDATE stt_processing_job AS stt
SET audio_cleanup_due_at = job.updated_at + INTERVAL '24 hours'
FROM processing_job AS job
WHERE job.id = stt.processing_job_id
  AND job.status = 'FAILED';

ALTER TABLE stt_processing_job
    ADD CONSTRAINT chk_stt_processing_job_audio_cleanup_time
        CHECK (
            audio_deleted_at IS NULL
            OR (
                audio_cleanup_due_at IS NOT NULL
                AND audio_deleted_at >= audio_cleanup_due_at
            )
        );

-- 정리 대상은 작업 ID 커서 순서로 반복 조회한다.
CREATE INDEX idx_stt_processing_job_audio_cleanup_cursor
    ON stt_processing_job (processing_job_id, audio_cleanup_due_at)
    WHERE audio_cleanup_due_at IS NOT NULL
      AND audio_deleted_at IS NULL;
