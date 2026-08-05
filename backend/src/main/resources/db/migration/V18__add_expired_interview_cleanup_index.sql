-- 진행 중인 세션을 ID 커서 순서로 만료 정리할 때 사용한다.
CREATE INDEX idx_interview_session_expired_cleanup
    ON interview_session (id, started_at)
    WHERE status = 'IN_PROGRESS';
