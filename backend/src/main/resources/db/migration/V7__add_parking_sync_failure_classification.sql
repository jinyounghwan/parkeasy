ALTER TABLE parking_sync_job
    ADD COLUMN failure_category VARCHAR(50) NULL AFTER last_error_message,
    ADD COLUMN root_error_type VARCHAR(100) NULL AFTER failure_category;
