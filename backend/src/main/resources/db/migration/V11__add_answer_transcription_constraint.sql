ALTER TABLE answer
    ADD CONSTRAINT chk_answer_transcription_completion
        CHECK (
            (transcript IS NULL AND transcribed_at IS NULL)
            OR
            (transcript IS NOT NULL AND transcribed_at IS NOT NULL)
        );
