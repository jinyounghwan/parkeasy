ALTER TABLE parking_sync_job
    ADD COLUMN invalid_realtime_count BIGINT NOT NULL DEFAULT 0 AFTER invalid_operation_count,
    ADD COLUMN zero_available_count BIGINT NOT NULL DEFAULT 0 AFTER missing_external_id_count,
    ADD COLUMN full_parking_count BIGINT NOT NULL DEFAULT 0 AFTER zero_available_count,
    ADD COLUMN missing_total_spaces_count BIGINT NOT NULL DEFAULT 0 AFTER full_parking_count,
    ADD COLUMN missing_available_spaces_count BIGINT NOT NULL DEFAULT 0 AFTER missing_total_spaces_count,
    ADD COLUMN relationship_invalid_count BIGINT NOT NULL DEFAULT 0 AFTER missing_available_spaces_count;
