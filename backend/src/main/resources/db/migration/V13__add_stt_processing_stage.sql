ALTER TABLE stt_processing_job
    ADD COLUMN processing_stage VARCHAR(20) NOT NULL DEFAULT 'TRANSCRIPTION';

ALTER TABLE stt_processing_job
    ALTER COLUMN processing_stage DROP DEFAULT;
