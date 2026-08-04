CREATE INDEX idx_interview_session_member_status_id
    ON interview_session (member_id, status, id);
