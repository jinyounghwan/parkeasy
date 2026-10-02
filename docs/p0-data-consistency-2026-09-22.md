# P0 데이터 정합성 audit 및 수정 결과

2026-09-22, 로컬 실제 MySQL 기준. 이 문서의 DB 수치는 SELECT 결과이며 fixture 수치가 아니다.

## 1. 실행 범위와 증거

- 기존 `.smoke-tools/P0Audit.java`, `p0-audit.sql`, `p0-extra.sql` 재사용. 보충 집계는 `p0-distribution.sql`.
- `Connection.setReadOnly(true)`, `autoCommit=false`, SELECT 이외 거부, 마지막 rollback 유지. 예외 경로에서도 rollback하도록 finally 보강.
- `${ENV_VAR:default}` 비밀번호 해석을 유지했고 비밀번호/접속 설정을 출력하지 않았다.
- 실제 MySQL에 UPDATE/DELETE/INSERT, migration, backfill, full sync를 실행하지 않았다. 외부 공공데이터 호출도 없었다.
- API 검증 서버: local profile, Hikari read-only, Flyway OFF, Hibernate ddl-auto=none, facility/operation/realtime scheduler OFF, 세 full-sync auto-resume OFF, localhost 8080 바인딩.
- 통합 테스트 fixture 생성은 별도 H2 메모리 DB에서만 수행.
- 원문: [DB audit](../.smoke-tools/p0-db-before.txt), [추가 audit](../.smoke-tools/p0-db-extra.txt), [분포](../.smoke-tools/p0-db-distribution.txt), [실제 API/성능](../.smoke-tools/p0-api-after.json), [UI 확인](../.smoke-tools/p0-ui.json).

## 2. id=141 실제 DB 값

| parking_lot 필드 | 값 |
|---|---|
| id | 141 |
| external_id | 04528-11191-00001-00-1 |
| name | 연세세브란스빌딩뒤 공영주차장 |
| parking_type | NULL (이름에 공영이 있어도 추론하지 않음) |
| total_spaces | 17 |
| base_time / base_fee | NULL / NULL |
| is_free / is_24_hours | false / false |
| latitude / longitude | 37.5575270 / 126.9766087 |

| parking_operation 필드 | 값 |
|---|---|
| parking_lot_id | 141 |
| base_free_time | 0 |
| base_time / base_fee | 30 / 3000 |
| additional_time / additional_fee | 10 / 0 |
| daily_max_fee / monthly_fee | 0 / 250000 |
| 월요일 | 08:00:00–22:00:00 |
| 화요일 | 08:00:00–22:00:00 |
| 수요일 | 08:00:00–22:00:00 |
| 목요일 | 08:00:00–22:00:00 |
| 금요일 | 08:00:00–22:00:00 |
| 토요일 | 09:00:00–22:00:00 |
| 일요일 | NULL / NULL |
| 공휴일 | NULL / NULL |

parking_realtime: `parking_lot_id=141`, `total_spaces=2`, `available_spaces=0`, `received_at=2026-09-08T20:51:07`.

## 3. 불일치 원인과 코드 흐름

시설 경로:

`prk_center_id / prk_plce_nm / prk_cmprt_co / prk_plce_entrc_la / prk_plce_entrc_lo`
→ [PublicParkingFacilityItem](../backend/src/main/java/com/parkeasy/backend/external/parking/PublicParkingFacilityItem.java)
→ [ParkingFacilityMapper.map](../backend/src/main/java/com/parkeasy/backend/external/parking/ParkingFacilityMapper.java)
→ `ParkingFacilityData`
→ [ParkingFacilityUpsertService.upsert/upsertOne](../backend/src/main/java/com/parkeasy/backend/external/parking/ParkingFacilityUpsertService.java)
→ [ParkingLot.builder/updateFacilityInfo](../backend/src/main/java/com/parkeasy/backend/parking/domain/ParkingLot.java).

이 경로는 이름·주소·위치·시설면수만 넣는다. baseFee/baseTime/parkingType을 넣지 않는다. 새 시설의 nullable 필드는 NULL, primitive boolean은 false로 남는다. 기존 시설 갱신도 요금 요약 필드를 건드리지 않는다.

운영 경로:

`basic_info.parking_chrge_bs_chrge / parking_chrge_bs_time`
→ [PublicParkingOperationItem.basicInfo](../backend/src/main/java/com/parkeasy/backend/external/parking/PublicParkingOperationItem.java)
→ [PublicParkingBasicInfo.baseFee/baseTime](../backend/src/main/java/com/parkeasy/backend/external/parking/PublicParkingBasicInfo.java)
→ [ParkingOperationParser.parse](../backend/src/main/java/com/parkeasy/backend/external/parking/ParkingOperationParser.java)
→ `ParkingOperationDetails` / `ParkingOperationCandidate`
→ `PublicParkingOperationSyncService` 또는 `PublicParkingOperationFullPageService`
→ [ParkingOperationUpsertService.upsert/upsertBatch](../backend/src/main/java/com/parkeasy/backend/external/parking/ParkingOperationUpsertService.java)
→ [ParkingOperation.updateOperationInfo](../backend/src/main/java/com/parkeasy/backend/parking/domain/ParkingOperation.java).

upsert는 `data_source=PUBLIC_DATA_PORTAL + external_id` 정확 일치로 시설을 찾고 `parking_operation`만 저장한다. `ParkingLot` 요약을 복제 갱신하지 않는다. fuzzy match는 없다.

이전 nearby는 `ParkingLotService.findNearby → NearbyParkingLotResponse.from → ParkingLot.baseFee/baseTime` 경로였으므로 NULL을 반환했고, detail은 `ParkingOperationRepository.findByParkingLotId(141) → ParkingOperationResponse.from → detail.operation`으로 3000/30을 반환했다. 동일 DB에 시설 NULL과 운영 3000/30이 함께 있는 사실과 각 쓰기 대상이 분리된 코드가 원인을 설명한다.

이전 실제 API 불일치 증거는 [기존 smoke 보고서](browser-smoke-2026-09-10.md)의 상세·realtime 항목에 있다. 이번 시작 시 localhost 서버는 꺼져 있었고, 작업 트리에는 이미 operation batch 수정 일부가 남아 있었다. 따라서 이전 바이너리의 API를 이번에 재현했다고 주장하지 않는다. 이번에 직접 재검증한 것은 DB 양쪽 값, 쓰기 경로, 완성된 수정 후 API이다. 과거 provider raw payload는 이번에 호출하지 않았으므로 특정 원본 응답의 수집 이력까지 소급 입증하지 않는다.

## 4. canonical source와 nearby 구조

- nearby와 detail의 기본·추가 요금, 운영시간 canonical source는 `parking_operation`.
- nearby: 시설 bounding box SELECT 1회 → 실제 반경 후보 ID → operation `WHERE parking_lot_id IN (...)` 1회 → realtime IN 조회 1회 → ID Map 결합 → 정렬 → limit.
- [ParkingLotService](../backend/src/main/java/com/parkeasy/backend/parking/service/ParkingLotService.java), [ParkingOperationRepository](../backend/src/main/java/com/parkeasy/backend/parking/repository/ParkingOperationRepository.java), [NearbyParkingLotResponse](../backend/src/main/java/com/parkeasy/backend/parking/dto/NearbyParkingLotResponse.java), [OperationSummary](../backend/src/main/java/com/parkeasy/backend/parking/dto/OperationSummary.java).
- operation이 없거나 해당 요금이 NULL이면 응답도 NULL. `parking_lot`의 과거 요금/boolean으로 fallback하지 않는다.
- FEE 정렬은 응답에 표시하는 operation baseFee 오름차순, NULL 마지막, 동률은 거리순. 기본요금 0과 무료 분류는 구분한다.
- detail 상위 호환 요금 필드도 operation에서 가져온다. 평일 요약 시간은 월–금 값이 모두 같을 때만 제공하고, 요일별 값은 operation에 유지한다.
- 반경 후보가 없으면 시설 1회만 실행하고 batch 조회는 생략한다. 후보가 있는 정상 요청은 총 3회이며 N+1이 없다.

## 5. 실제 분포와 필터 정책

parking_lot 총 267,369행:

| data_source | 총수 | PUBLIC | PRIVATE | NULL |
|---|---:|---:|---:|---:|
| PUBLIC_DATA_PORTAL | 267,363 | 4 | 0 | 267,359 |
| MANUAL | 6 | 1 | 5 | 0 |
| 전체 | 267,369 | 5 | 5 | 267,359 |

| DB 필드 | true | false | NULL |
|---|---:|---:|---:|
| parking_lot.is_free | 3 | 267,366 | 0 |
| parking_lot.is_24_hours | 4 | 267,365 | 0 |

유형이 있는 10행, free=true 3행, 24시간=true 4행은 모두 id 1–10의 `SAMPLE-*` 개발 샘플이다. 샘플 제외 267,359행의 유형은 전부 NULL이고 두 legacy boolean은 모두 false. nearby repository는 SAMPLE-*를 제외한다. 따라서 지역별 필터 0건을 필터 조건식 버그라고 볼 수 없다.

PUBLIC_DATA_PORTAL 시설 267,363행 중 operation 연결 267,359행, coverage **99.9985%**. 샘플 제외 실수집은 267,359/267,359로 **100%**. 연결 누락과 operation 내부 항목의 결측을 구분해야 한다.

| 필터 | source 및 결정 |
|---|---|
| 공영/민영 | 현재 PublicParkingFacilityItem/Mapper에 원본 유형 필드가 없다. 이름, 요금, 주소, data_source로 추론하지 않는다. UI disabled + 데이터 미지원 안내. backend parkingType 조건은 기존 DB 컬럼의 정확 일치 의미를 유지한다. |
| 무료 | 현재 연동 DTO에 명시적인 무료구분이 없다. baseFee=0만으로 확정 불가. 모든 요금 0이라는 조합도 공식 무료 분류로 새로 정의하지 않는다. nearby/detail isFree=null, UI disabled. API free 파라미터는 true/false 모두 HTTP 400 unsupported. |
| 24시간 | 명시 분류가 없다. parser의 000000~000000 → 둘 다 NULL 정책 유지. 00:00~23:00 또는 00:00~23:59:59 조합을 새 분류 규칙으로 만들지 않는다. is24Hours=null, UI disabled. API open24Hours true/false 모두 HTTP 400 unsupported. |

현재 코드/문서에서 기존 provider 분류 정책의 근거는 확인되지 않았다. 작업 시작 시 남아 있던 `OperationSummary`의 4개 요금 0 / 전 요일 23:59:59 판정은 검증되지 않은 수정안으로 보고 제거했다. **공식 API 전체에 필드가 절대로 없다는 주장이 아니라 현재 연동 계약에 분류 source가 확보되어 있지 않다는 결론**이다. source 문서와 명시 필드를 확보한 뒤 필터를 다시 활성화해야 한다.

legacy DB false는 유료/비24시간의 증명이 아니다. API null은 미상이며 UI는 '요금 확인', '운영시간 확인'으로 표현한다. DB 값을 새 boolean으로 채우지 않았다.

## 6. operation 내부 결측

분모는 실제 operation 전체 **267,359행**. 시간 known은 시작/종료 둘 다 있는 pair이며, weekday는 월–금 5쌍이 모두 있는 경우이다.

| 항목 | known | NULL 또는 pair 불완전 | known % |
|---|---:|---:|---:|
| baseFee | 9,392 | 257,967 | 3.5129% |
| baseTime | 267,359 | 0 | 100.0000% |
| additionalFee | 9,392 | 257,967 | 3.5129% |
| 월요일 pair | 5,808 | 261,551 | 2.1724% |
| 평일 전체 | 5,808 | 261,551 | 2.1724% |
| 토요일 | 5,193 | 262,166 | 1.9423% |
| 일요일 | 4,622 | 262,737 | 1.7288% |
| 공휴일 | 4,622 | 262,737 | 1.7288% |

- base_fee=0: **5,825행**.
- 주요 단기 요금 `base_fee=0 AND additional_fee=0 AND daily_max_fee=0`: **5,757행**.
- 여기에 monthly_fee=0까지 요구하면 **5,499행**. 이는 숫자 분포이며 무료 주차장 수가 아니다. 기존 extra SQL의 `canonical_free` alias는 이전 수정안의 가설명이며 채택된 정책이 아니다.
- 월요일 NULL/NULL은 261,155행, 00:00/NULL 382행, 00:00/23:00 2,809행이다. 00:00/23:00을 24시간으로 바꾸지 않는다.
- 상세가 비어 보이는 주된 DB 근거는 연결 부재가 아니라 연결된 operation의 요금·시간 값 결측이다. parser는 빈 값/잘못된 정수·시간도 NULL로 만들며, 000000 쌍도 NULL로 만든다. 원본 raw 이력이 없으므로 각 NULL이 원본 부재인지 파싱 실패인지까지는 이번 audit으로 구별할 수 없다.

## 7. 시설면수와 실시간면수

parking_realtime과 시설을 연결한 전체 행 기준:

| linked | same | different | realtime > facility | realtime < facility | incomparable(NULL) |
|---:|---:|---:|---:|---:|---:|
| 195 | 90 | 105 | 26 | 79 | 0 |

시설 총 주차면수는 `parking_lot.total_spaces`. realtime은 `available_spaces` 중심이며 `realtime.total_spaces`는 상세의 '실시간 집계 기준'으로 분리한다. 시설 총면수를 덮어쓰거나 두 값의 일치를 강제하지 않는다. 실제 차이의 provider 업무상 원인은 별도 명세 확인 대상이며 DB 분포만으로 같은 의미라고 가정하지 않는다.

## 8. 수정 후 실제 API/필터 검증

GET `/api/parking-lots/nearby?latitude=37.557527&longitude=126.9766087&radius=500&limit=100`
및 GET `/api/parking-lots/141`:

| 항목 | nearby | detail 상위 필드 | detail.operation |
|---|---:|---:|---:|
| baseFee | 3000 | 3000 | 3000 |
| baseTime | 30 | 30 | 30 |
| isFree / is24Hours | null / null | null / null | 분류 필드 없음 |

시설 총면수 17, realtime 총면수 2, 잔여 0, stale=true도 유지됨.

- FREE/24H: 유효한 실제 분류 source가 없어 긍정 결과 테스트를 가장하지 않았다. 실제 GET으로 true/false 모두 400, DB 조회 0회를 확인했다.
- id=66: 실제 DB 요금 0 행 근처 GET에서 baseFee=0, isFree=null을 검증했다. 무료로 승격하지 않는다.
- 공영/민영: 샘플 이외 실데이터가 전부 NULL이므로 브라우저 0건 테스트를 반복하지 않았다.
- 유효한 realtimeAvailable=true: id=141 위치에서 6행, id=141 포함 확인.
- 서울역 FEE limit=300: 300행의 operation baseFee 오름차순 확인, 요금 미상 246행은 마지막.
- Chrome desktop 1440×900 / mobile 390×844: 네 필터 disabled, 데이터 미지원 안내, id=141 목록/상세 30분 3,000원, 독립 총면수, 가로 넘침 없음, JS 오류 없음 확인. 새 안내의 모바일 줄바꿈도 수정 후 확인했다. 지도/Places 전체 재검증은 이번 좁은 UI 확인 범위에 포함하지 않았다.

## 9. 성능과 SQL 수

로컬 MySQL, 2km, limit=100, sort=FEE. 지점별 warm-up 1회 후 순차 GET 3회. 시간은 Node fetch 시작부터 JSON 수신/파싱 완료까지이며 SQL DEBUG 로그 활성 상태이다. 부하 테스트/운영 p95가 아니다.

| 위치 | 시설 query | operation query | realtime query | 전체 query | 3회 응답시간(ms) | 평균(ms) |
|---|---:|---:|---:|---:|---|---:|
| 수원역 | 1 | 1 | 1 | 3 | 78.36 / 69.99 / 75.58 | 74.64 |
| 성남역 인근(37.3947,127.1200) | 1 | 1 | 1 | 3 | 61.68 / 43.31 / 63.00 | 56.00 |
| 판교역 | 1 | 1 | 1 | 3 | 44.35 / 49.57 / 48.06 | 47.33 |
| 서울역 | 1 | 1 | 1 | 3 | 72.24 / 72.78 / 75.31 | 73.44 |
| 종각역/종로 | 1 | 1 | 1 | 3 | 69.07 / 71.26 / 74.60 | 71.64 |

모든 측정에서 100행 반환. 로그의 SQL 문 실행 수를 세었으며 시설 SQL 내부 realtime EXISTS는 별도 round-trip으로 세지 않았다. H2 통합 테스트도 persistence context flush/clear 후 Hibernate prepareStatementCount=3을 검증한다. 운영 entity의 시설 연관은 LAZY이고 이미 읽은 시설 ID를 Map key로 사용한다.

## 10. scheduler와 자동 시작

- `application.properties`: `parkeasy.sync.facility.enabled=false`, `operation.enabled=false`, `realtime.enabled=false`.
- `application-local.properties`: 위 scheduler ON override 없음. 기본 설정 false를 상속한다.
- [ParkingSyncScheduler](../backend/src/main/java/com/parkeasy/backend/sync/ParkingSyncScheduler.java)는 각 scheduled callback 시작에서 enabled=false이면 반환한다. 예약 callback 등록과 실제 sync 실행은 구분한다.
- full sync 시작은 각 FullSyncService의 명시적 start/resume 진입으로만 수행. 자동 시작용 ApplicationRunner/CommandLineRunner/PostConstruct/ApplicationReady listener는 없다.
- 두 properties의 full-sync 세 종류 `auto-resume-enabled=true`는 **수동으로 시작된 full sync job 내부의 일시 장애 복구** 정책이다. scheduler ON, 시작 시 전국 sync, 과거 job의 부팅 자동 재개를 뜻하지 않는다.
- 이번 검증 서버에서는 auto-resume도 세 종류 모두 false로 강제했다. 실제 scheduler/full sync 실행은 없었다.

## 11. 코드/검증 파일

이전 작업에서 남아 있던 batch 수정까지 포함해 이번 결과의 핵심 파일:

- backend `ParkingLotService`, `ParkingOperationRepository`, `ParkingLotRepository`: operation/realtime batch와 시설 후보 query, 표시값 기준 FEE 정렬, 미지원 필터 거부.
- backend DTO `NearbyParkingLotResponse`, `ParkingLotDetailResponse`, `OperationSummary`: operation 기반 요금과 nullable 분류. `ParkingOperationResponse`는 기존 매핑 사용.
- backend `ParkingLotServiceTest`, `ParkingCanonicalIntegrationTest`: canonical 기본요금/시간, operation 없음, batch 1회씩, FEE 정렬, unsupported 필터, 총면수 불일치 허용, nearby/detail 일치, 실제 SQL 3회.
- frontend `SearchControls.tsx`, `App.css`, `types/parking.ts`, `utils/format.ts`: disabled 안내와 nullable 타입/포맷.
- `.smoke-tools/P0Audit.java`, `p0-distribution.sql`, `p0-verify.cjs`, `p0-ui.cjs` 및 결과 파일.
- 이 문서.

작업 폴더에 .git 메타데이터가 없어 이전 변경과 이번 변경을 git diff로 구분한 목록은 아니다. 처음부터 존재하던 수정은 보존·검토했다.

## 12. 테스트/빌드 결과와 남은 작업

- Backend `gradlew --gradle-user-home D:/dev/parkeasy/.gradle-user-home clean test integrationTest build --console=plain`: **BUILD SUCCESSFUL**. unit 103, integration 13, failures/errors/skipped 모두 0.
- Gradle 기본 캐시 C:\.gradle 쓰기 실패 후 승인된 실행으로 프로젝트 캐시 사용. 통합 테스트는 H2이며 외부 liveApiTest는 실행하지 않았다.
- Frontend `npm run lint`, `npm run build`: 최종 UI 줄바꿈 수정 후에도 통과.
- 실제 MySQL GET 검증, SQL 계수, desktop/mobile UI 확인 통과.
- 시작용 기존 Start-Process 스크립트는 환경의 Path/PATH 중복 오류로 실행되지 않아, 동일 안전 옵션으로 Java를 직접 실행했다.

**P0 해결:** 이번 범위인 nearby/detail 요금 source drift, 불명확한 boolean 판정, 항상 0건인 분류 필터 UX, 시설/realtime 면수 혼용 방지. DB legacy 요약 필드를 수정하지 않아도 해당 응답은 canonical source를 사용한다.

**남은 P0:** 검증한 nearby/detail 경로에서 재현된 미해결 P0는 없다. 다만 전국 데이터의 완전성이 해결된 것은 아니며 네 필터를 지원 완료로 간주하면 안 된다.

**P1:** 공식 유형/무료/24시간 source 확보 후 필터 재개, 원본 결측/파싱 실패 구분 가능성 조사, 오래된 realtime 공급 상태 확인. UI가 쓰지 않는 기존 전체목록 `GET /api/parking-lots`는 여전히 `ParkingLotSummaryResponse.from(ParkingLot)` legacy 요약을 사용하므로 canonical화 또는 API 폐기/축소가 별도 필요하다. 이번 수정·실제 GET 검증 범위는 nearby/detail이다.

**P2:** legacy summary 컬럼 정리/migration은 별도 승인 범위, 전체목록 pagination, 최대 10km 후보의 메모리/IN 크기 및 동시 부하 성능 검증. 이번에 일괄 보정하거나 schema를 변경하지 않았다.
