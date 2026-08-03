CREATE TABLE stt_processing_job
(
    processing_job_id  BIGINT        NOT NULL,
    answer_id          BIGINT        NOT NULL,
    audio_object_key   VARCHAR(1024) NOT NULL,
    source_object_etag VARCHAR(255)  NOT NULL,

    CONSTRAINT pk_stt_processing_job
        PRIMARY KEY (processing_job_id),

    CONSTRAINT uk_stt_processing_job_answer_id
        UNIQUE (answer_id),

    CONSTRAINT fk_stt_processing_job_processing_job
        FOREIGN KEY (processing_job_id)
            REFERENCES processing_job (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_stt_processing_job_answer
        FOREIGN KEY (answer_id)
            REFERENCES answer (id)
);

CREATE TABLE answer_analysis_processing_job
(
    processing_job_id BIGINT NOT NULL,
    answer_id         BIGINT NOT NULL,

    CONSTRAINT pk_answer_analysis_processing_job
        PRIMARY KEY (processing_job_id),

    CONSTRAINT uk_answer_analysis_processing_job_answer_id
        UNIQUE (answer_id),

    CONSTRAINT fk_answer_analysis_processing_job_processing_job
        FOREIGN KEY (processing_job_id)
            REFERENCES processing_job (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_answer_analysis_processing_job_answer
        FOREIGN KEY (answer_id)
            REFERENCES answer (id)
);

CREATE TABLE final_feedback_processing_job
(
    processing_job_id BIGINT NOT NULL,

    CONSTRAINT pk_final_feedback_processing_job
        PRIMARY KEY (processing_job_id),

    CONSTRAINT fk_final_feedback_processing_job_processing_job
        FOREIGN KEY (processing_job_id)
            REFERENCES processing_job (id)
            ON DELETE CASCADE
);

-- V9에서 생성된 STT 작업은 검증한 ETag가 없으므로 안전하게 실패 처리한 뒤 상세 테이블로 이관한다.
INSERT INTO stt_processing_job (
    processing_job_id,
    answer_id,
    audio_object_key,
    source_object_etag
)
SELECT processing_job.id,
       processing_job.answer_id,
       answer.audio_object_key,
       'LEGACY_UNVERIFIED'
FROM processing_job
JOIN answer ON answer.id = processing_job.answer_id
WHERE processing_job.job_type = 'STT_TRANSCRIPTION';

UPDATE processing_job
SET status        = 'FAILED',
    error_code    = 'AUDIO_VERSION_NOT_BOUND',
    error_message = '검증된 음성 객체 ETag가 없어 처리할 수 없는 기존 작업입니다.',
    updated_at    = CURRENT_TIMESTAMP
WHERE job_type = 'STT_TRANSCRIPTION'
  AND status IN ('QUEUED', 'PROCESSING', 'RETRY_WAIT');

INSERT INTO answer_analysis_processing_job (
    processing_job_id,
    answer_id
)
SELECT id, answer_id
FROM processing_job
WHERE job_type = 'ANSWER_ANALYSIS';

INSERT INTO final_feedback_processing_job (processing_job_id)
SELECT id
FROM processing_job
WHERE job_type = 'FINAL_FEEDBACK_GENERATION';

ALTER TABLE processing_job
    DROP CONSTRAINT chk_processing_job_target;

DROP INDEX uk_processing_job_answer_type;
DROP INDEX uk_processing_job_session_type;

ALTER TABLE processing_job
    DROP CONSTRAINT fk_processing_job_answer,
    DROP COLUMN answer_id;

-- PostgreSQL은 외래 키 인덱스를 자동 생성하지 않으므로 세션 연쇄 삭제를 위한 인덱스를 명시한다.
CREATE INDEX idx_processing_job_interview_session_id
    ON processing_job (interview_session_id);
