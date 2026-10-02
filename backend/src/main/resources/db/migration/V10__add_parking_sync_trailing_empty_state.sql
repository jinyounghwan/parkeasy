ALTER TABLE parking_sync_job
    ADD COLUMN consecutive_empty_page_count INT NOT NULL DEFAULT 0 AFTER current_empty_success_count,
    ADD COLUMN first_trailing_empty_page INT NULL AFTER consecutive_empty_page_count,
    ADD COLUMN last_data_page INT NULL AFTER first_trailing_empty_page,
    ADD COLUMN completion_reason VARCHAR(100) NULL AFTER last_data_page;
