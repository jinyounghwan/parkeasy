CREATE TABLE parking_sync_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sync_type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at DATETIME NULL,
    ended_at DATETIME NULL,
    last_success_page INT NOT NULL DEFAULT 0,
    next_page INT NOT NULL DEFAULT 1,
    num_of_rows INT NOT NULL,
    total_pages BIGINT NULL,
    total_count BIGINT NULL,
    processed_count BIGINT NOT NULL DEFAULT 0,
    inserted_count BIGINT NOT NULL DEFAULT 0,
    updated_count BIGINT NOT NULL DEFAULT 0,
    skipped_count BIGINT NOT NULL DEFAULT 0,
    duplicate_count BIGINT NOT NULL DEFAULT 0,
    failed_page INT NULL,
    last_error_type VARCHAR(100) NULL,
    last_error_message VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_parking_sync_job_type_created (sync_type, created_at),
    KEY idx_parking_sync_job_type_status (sync_type, status)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
