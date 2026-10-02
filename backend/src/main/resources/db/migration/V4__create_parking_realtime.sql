CREATE TABLE parking_realtime (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parking_lot_id BIGINT NOT NULL,
    total_spaces INT NULL,
    available_spaces INT NULL,
    observed_at DATETIME NULL,
    received_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_parking_realtime_parking_lot_id (parking_lot_id),
    CONSTRAINT fk_parking_realtime_parking_lot
        FOREIGN KEY (parking_lot_id) REFERENCES parking_lot (id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
