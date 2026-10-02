CREATE TABLE parking_lot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    address VARCHAR(500) NULL,
    latitude DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,
    parking_type VARCHAR(30) NULL,
    total_spaces INT NULL,
    base_time INT NULL,
    base_fee INT NULL,
    additional_time INT NULL,
    additional_fee INT NULL,
    daily_max_fee INT NULL,
    weekday_open_time TIME NULL,
    weekday_close_time TIME NULL,
    is_free BOOLEAN NOT NULL DEFAULT FALSE,
    is_24_hours BOOLEAN NOT NULL DEFAULT FALSE,
    data_source VARCHAR(50) NULL,
    external_id VARCHAR(100) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_parking_lot_source_external_id (data_source, external_id),
    KEY idx_parking_lot_latitude_longitude (latitude, longitude)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
