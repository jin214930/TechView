CREATE INDEX idx_interview_session_member_started_at
    ON interview_session (member_id, started_at);
