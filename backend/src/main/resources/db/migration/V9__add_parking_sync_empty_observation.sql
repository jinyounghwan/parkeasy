ALTER TABLE parking_sync_job
    ADD COLUMN current_empty_page_no INT NULL AFTER consecutive_failure_count,
    ADD COLUMN current_empty_success_count INT NOT NULL DEFAULT 0 AFTER current_empty_page_no;
