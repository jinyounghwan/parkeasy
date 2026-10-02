INSERT INTO parking_lot (
    name, address, latitude, longitude, parking_type, total_spaces,
    base_time, base_fee, additional_time, additional_fee, daily_max_fee,
    weekday_open_time, weekday_close_time, is_free, is_24_hours,
    data_source, external_id, created_at, updated_at
) VALUES
('수원시청 테스트 공영주차장', '경기도 수원시 팔달구 효원로 테스트 1', 37.2635737, 127.0286009, 'PUBLIC', 120, 30, 1000, 10, 500, 10000, '06:00:00', '23:00:00', FALSE, FALSE, 'PUBLIC_DATA_PORTAL', 'SAMPLE-001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('인계동 테스트 민영주차장', '경기도 수원시 팔달구 인계로 테스트 2', 37.2651000, 127.0302000, 'PRIVATE', 45, 30, 1500, 10, 700, 15000, '07:00:00', '22:00:00', FALSE, FALSE, 'MANUAL', 'SAMPLE-002', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('효원공원 테스트 무료주차장', '경기도 수원시 팔달구 동수원로 테스트 3', 37.2622000, 127.0365000, 'PUBLIC', 80, 60, 0, 30, 0, 0, '09:00:00', '21:00:00', TRUE, FALSE, 'MANUAL', 'SAMPLE-003', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('수원올림픽공원 테스트 주차장', '경기도 수원시 팔달구 권광로 테스트 4', 37.2610000, 127.0298000, 'PUBLIC', 65, 30, 800, 10, 300, 8000, NULL, NULL, FALSE, TRUE, 'PUBLIC_DATA_PORTAL', 'SAMPLE-004', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('인계예술공원 샘플 주차장', '경기도 수원시 팔달구 경수대로 테스트 5', 37.2670000, 127.0325000, 'PRIVATE', 30, 20, 1000, 10, 500, 12000, '08:00:00', '23:59:59', FALSE, FALSE, 'MANUAL', 'SAMPLE-005', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('팔달구청 방향 테스트 주차장', '경기도 수원시 팔달구 창룡대로 테스트 6', 37.2648000, 127.0255000, 'PUBLIC', 100, 60, 0, 30, 0, NULL, NULL, NULL, TRUE, TRUE, 'PUBLIC_DATA_PORTAL', 'SAMPLE-006', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('권광로 테스트 민영주차장', '경기도 수원시 팔달구 권광로 테스트 7', 37.2597000, 127.0314000, 'PRIVATE', 55, 30, 2000, 10, 800, 18000, '00:00:00', '23:59:59', FALSE, TRUE, 'MANUAL', 'SAMPLE-007', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('매탄동 경계 샘플 주차장', '경기도 수원시 영통구 매탄로 테스트 8', 37.2663000, 127.0400000, 'PRIVATE', 25, 15, 1000, 15, 1000, NULL, '10:00:00', '20:00:00', FALSE, FALSE, 'MANUAL', 'SAMPLE-008', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('수원시청역 테스트 환승주차장', '경기도 수원시 팔달구 권선로 테스트 9', 37.2619000, 127.0307000, 'PUBLIC', 200, 30, 500, 10, 200, 6000, NULL, NULL, FALSE, TRUE, 'PUBLIC_DATA_PORTAL', 'SAMPLE-009', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('인계광장 샘플 무료주차장', '경기도 수원시 팔달구 인계로 테스트 10', 37.2641000, 127.0337000, 'PRIVATE', 20, 60, 0, 30, 0, 0, '09:00:00', '18:00:00', TRUE, FALSE, 'MANUAL', 'SAMPLE-010', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
