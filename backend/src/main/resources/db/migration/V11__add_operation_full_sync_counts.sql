ALTER TABLE parking_sync_job
    ADD COLUMN orphan_count BIGINT NOT NULL DEFAULT 0 AFTER duplicate_count,
    ADD COLUMN invalid_operation_count BIGINT NOT NULL DEFAULT 0 AFTER orphan_count,
    ADD COLUMN missing_external_id_count BIGINT NOT NULL DEFAULT 0 AFTER invalid_operation_count;
