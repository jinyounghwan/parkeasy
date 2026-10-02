ALTER TABLE parking_sync_job
    ADD COLUMN page_limit INT NULL AFTER num_of_rows,
    ADD COLUMN successful_pages INT NOT NULL DEFAULT 0 AFTER page_limit,
    ADD COLUMN current_page INT NULL AFTER successful_pages,
    ADD COLUMN auto_resume_attempts INT NOT NULL DEFAULT 0 AFTER current_page,
    ADD COLUMN consecutive_failure_count INT NOT NULL DEFAULT 0 AFTER auto_resume_attempts,
    ADD COLUMN last_failure_at DATETIME NULL AFTER consecutive_failure_count,
    ADD COLUMN last_resume_at DATETIME NULL AFTER last_failure_at;
