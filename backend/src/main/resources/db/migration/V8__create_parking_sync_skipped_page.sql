ALTER TABLE parking_sync_job
    ADD COLUMN skipped_page_count INT NOT NULL DEFAULT 0 AFTER duplicate_count;

CREATE TABLE parking_sync_skipped_page (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    page_no INT NOT NULL,
    skip_reason VARCHAR(50) NOT NULL,
    attempt_count INT NOT NULL,
    observed_item_count INT NOT NULL,
    total_count BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_parking_sync_skipped_job_page (job_id, page_no),
    KEY idx_parking_sync_skipped_job_created (job_id, created_at),
    CONSTRAINT fk_parking_sync_skipped_job
        FOREIGN KEY (job_id) REFERENCES parking_sync_job (id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
