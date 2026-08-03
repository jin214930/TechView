CREATE INDEX idx_processing_job_status_lease_expires_at
    ON processing_job (status, lease_expires_at);
