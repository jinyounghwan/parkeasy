ALTER TABLE parking_sync_job
    ADD COLUMN realtime_matched_count BIGINT NOT NULL DEFAULT 0 AFTER relationship_invalid_count,
    ADD COLUMN zero_available_matched_count BIGINT NOT NULL DEFAULT 0 AFTER realtime_matched_count,
    ADD COLUMN zero_available_orphan_count BIGINT NOT NULL DEFAULT 0 AFTER zero_available_matched_count;
