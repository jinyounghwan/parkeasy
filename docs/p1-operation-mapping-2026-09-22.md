# 운영정보 결측 및 필드 매핑 분석

전국/단일 외부 API를 재호출하지 않았다. 현재 DTO·parser·entity·upsert·migration·기존 테스트, 새 synthetic fixture, 저장 DB 집계만 사용했다. 실제 수집 당시 raw JSON은 저장되어 있지 않다. 따라서 **현재 코드의 재현 가능한 변환 정책**과 **과거 DB 각 NULL의 실제 발생 원인**은 구분한다.

## 확인한 실제 DB

[Read-only audit](../.smoke-tools/p1-db.txt), 2026-09-22:

| 필드 | known / 267,359 | known 비율 | 0인 행 |
|---|---:|---:|---:|
| baseFreeTime | 267,359 | 100% | 이번 별도 집계 없음 |
| baseTime | 267,359 | 100% | 0 |
| baseFee | 9,392 | 3.5129% | 5,825 |
| additionalTime | 267,359 | 100% | 0 |
| additionalFee | 9,392 | 3.5129% | 7,776 |
| dailyMaxFee | 9,392 | 3.5129% | 이전 P0 집계 참조 |
| monthlyFee | 9,391 | 3.5125% | 이전 P0 집계 참조 |

baseTime은 모든 행이 30이다. 현재 parser/entity/migration에는 30을 기본값으로 생성하는 코드가 없다. 이 분포는 관측 사실이며, provider 원본의 기본값인지 과거 처리 경로의 결과인지는 보관 raw 없이 확정할 수 없다.

P0에서 측정한 시간 pair known 비율은 월–금 모두 2.1724%, 토요일 1.9423%, 일요일/공휴일 각각 1.7288%다. 실수집 시설의 operation 연결 coverage는 100%여서 내부 필드 결측과 FK 연결 누락은 다르다.

## 전체 DTO 매핑

경로: `PublicDataParkingOperationClient`의 JsonMapper → `PublicParkingOperationResponse` → `PublicParkingOperationItem` → `ParkingOperationParser.parse` → `ParkingOperationDetails` → `ParkingOperationCandidate` → `ParkingOperationUpsertService.upsert/upsertBatch` → `ParkingOperation.updateOperationInfo`.

`I` = trim 후 Integer.valueOf. blank/null/형식 오류/Integer 범위 초과는 null, 숫자 0은 보존.
`T` = 시간쌍 trim, 둘 다 000000이면 둘 다 null, 그 외 각 값을 6자리 HHmmss로 LocalTime 변환. 잘못된 각 값은 null.

| provider field | DTO field | parser | entity field |
|---|---|---|---|
| prk_center_id | item.externalId | sync에서 trim/식별자 검증 | parkingLot FK: PUBLIC_DATA_PORTAL + externalId 정확 일치 |
| opertn_bs_free_time | item.baseFreeTime | I | baseFreeTime |
| basic_info.parking_chrge_bs_time | basicInfo.baseTime | I | baseTime |
| basic_info.parking_chrge_bs_chrge | basicInfo.baseFee | I | baseFee |
| basic_info.parking_chrge_adit_unit_time | basicInfo.additionalTime | I | additionalTime |
| basic_info.parking_chrge_adit_unit_chrge | basicInfo.additionalFee | I | additionalFee |
| fxamt_info.parking_chrge_one_day_chrge | fixedAmountInfo.dailyMaxFee | I | dailyMaxFee |
| fxamt_info.parking_chrge_mon_unit_chrge | fixedAmountInfo.monthlyFee | I | monthlyFee |
| Monday.opertn_start_time | monday.startTime | T | mondayOpenTime |
| Monday.opertn_end_time | monday.endTime | T | mondayCloseTime |
| Tuesday.opertn_start_time | tuesday.startTime | T | tuesdayOpenTime |
| Tuesday.opertn_end_time | tuesday.endTime | T | tuesdayCloseTime |
| Wednesday.opertn_start_time | wednesday.startTime | T | wednesdayOpenTime |
| Wednesday.opertn_end_time | wednesday.endTime | T | wednesdayCloseTime |
| Thursday.opertn_start_time | thursday.startTime | T | thursdayOpenTime |
| Thursday.opertn_end_time | thursday.endTime | T | thursdayCloseTime |
| Friday.opertn_start_time | friday.startTime | T | fridayOpenTime |
| Friday.opertn_end_time | friday.endTime | T | fridayCloseTime |
| Saturday.opertn_start_time | saturday.startTime | T | saturdayOpenTime |
| Saturday.opertn_end_time | saturday.endTime | T | saturdayCloseTime |
| Sunday.opertn_start_time | sunday.startTime | T | sundayOpenTime |
| Sunday.opertn_end_time | sunday.endTime | T | sundayCloseTime |
| Holiday.opertn_start_time | holiday.startTime | T | holidayOpenTime |
| Holiday.opertn_end_time | holiday.endTime | T | holidayCloseTime |

응답 envelope도 모두 확인했다:

| provider field | DTO field | 사용처 / operation에 직접 저장하지 않는 이유 |
|---|---|---|
| PrkOprInfo | response.items | 항목 배열, 각 item을 위 경로로 저장 |
| pageNo | response.pageNo | 페이지 메타데이터, 요청 pageNo/job 진행 상태와 구분 |
| resultCode | response.resultCode | 성공 검증, 개별 주차장 운영값 아님 |
| resultMsg | response.resultMsg | provider 결과 설명, 운영 entity 필드 아님 |
| totalCount | response.totalCount | 종료 판단 및 full-sync job 진행량 |
| numOfRows | response.numOfRows | 응답 페이지 메타데이터, 호출 시 요청 rows와 구분 |

현재 모델링된 데이터 필드 23개(숫자 7 + 시간 16)와 식별자 1개에 매핑 누락은 없다. `id`, `createdAt`, `updatedAt`은 DB/애플리케이션에서 생성하며 provider 매핑 대상이 아니다. 이 표는 **현재 알려진 DTO 계약의 전수표**이며, 확보하지 못한 provider 전체 명세에 추가 필드가 없다는 증명은 아니다.

## null 변환 정책

| 입력 | 숫자 필드 | 시간 필드 |
|---|---|---|
| null, 빈 문자열, 공백 | null | null |
| "0" | **0 보존** | 6자리 형식이 아니므로 null |
| "000000" | **0 보존** | 상대 시간과 함께 판정; 단독 유효 자정은 00:00 |
| 두 필드가 각각 "000000" | 해당 없음 | 두 값 모두 null: 기존 unknown/closed 정책 |
| "000000~000000" 한 문자열 | 숫자 형식 오류 | 시간 필드 형식 오류; range 문자열을 받는 계약 아님 |
| "1,200", "30분", "1.5", Integer 초과 | null | 해당 없음 |
| "240000", "126000", "0900", "09:00:00" | 문맥에 따라 숫자 파싱 | 시간 범위/형식 오류 → null |
| 앞뒤 공백 + 정상값 | trim 후 정상값 | trim 후 정상값 |

`000000 → null`이 전 시간에 일괄 적용되는 것은 아니다. 000000~180000은 자정~18시, 090000~000000은 09시~자정을 보존한다. 0원/0분을 null로 바꾸는 버그는 발견되지 않았다. malformed 값의 관대한 재해석, 무료/24시간 추론, 임의 기본값 생성은 추가하지 않았다.

## 필드별 원인 분류 및 한계

| 대상 | provider 결측 | mapper/잘못된 field mapping | parser 정책 | DB 저장 |
|---|---|---|---|---|
| baseFreeTime/baseTime/additionalTime | 현재 DB에는 결측 없음; 원본 내용은 미보관 | 알려진 계약/fixture와 일치 | blank/invalid이면 null이나 현재 DB known 100% | 0/정상값 보존 확인 |
| baseFee/additionalFee/dailyMaxFee | 원본 누락·빈 값 가능, **과거 행별 확정 불가** | 현재 필드명 누락/교차 매핑 재현 안 됨 | blank/invalid/overflow도 null 발생 경로 | 0/정상/null 보존 확인 |
| monthlyFee | 위와 동일, known이 다른 요금보다 1행 적음 | monthly 전용 필드 정상 매핑 | 월정기만 있는 item도 hasAnyValue=true | 월정기만 있는 행 저장 가능 |
| 월–금 | 원본 미보관으로 결측/이상값 구분 불가 | 각 요일 시작·종료 독립 매핑 확인 | 000000 쌍 의도적 null, malformed null | 각 요일 독립 저장 확인 |
| 토/일/공휴일 | 위와 동일 | Saturday/Sunday/Holiday 대소문자·필드 경로 일치 | 평일과 같은 정책 | 독립 저장 확인 |

현재 코드에서 mapper 누락, 정상 0 소실, DB 변환 버그는 재현되지 않았다. **“전부 provider 원본 결측이다” 또는 “파서 오류다”라고 단정할 근거도 없다.** 과거 raw를 저장하지 않았고 필드별 blank/invalid/null-pair 계수도 없으므로 이번 허용 자료만으로 A/B/C를 모든 행에 배타적으로 확정할 수 없다.

추가 저장 특성: operation upsert는 최신 details를 전체 교체한다. 새 details의 null도 기존 값을 덮는다. 중복 externalId는 마지막 유효 item을 선택하며, baseTime처럼 한 필드라도 있으면 hasAnyValue=true다. 따라서 sparse snapshot이 기존 값을 지웠을 가능성도 있으나, 과거 raw/변경 이력이 없어 발생 여부와 규모는 알 수 없다. source가 전체 snapshot인지 patch인지 확인 없이 null merge 정책을 바꾸지 않았다.

## 테스트 보강

- [operation-cases.json](../backend/src/test/resources/fixtures/operation-cases.json): **실제 API 응답을 보관한 파일이 아닌 synthetic contract fixture**. 정상 모든 필드, 0원/0분, 추가요금 0, 자정/자정쌍, blank, malformed, 월정기만, 시간만, nested 필드 누락의 9개 item.
- `ParkingOperationFixtureTest`: JSON→DTO→parser, 23개 entity 필드, 요일마다 다른 시간을 사용한 오매핑 검출, 0 보존 및 null 정책.
- `OperationFixturePersistenceIntegrationTest`: 동일 synthetic 응답→sync service의 가짜 client→upsert→H2 flush/clear→reload→23개 값 비교. 유효 6행 저장, 완전 결측/blank/malformed 3행 skip.
- 외부 API 호출 없음. 운영 mapping/parser는 수정하지 않았다.

2026-09-23 후속: 아래 quality metric을 최소 구현했다. 실제 수집은 실행하지 않았다.

## null overwrite 정책 최종 판단 (2026-09-23)

현재 insert는 parser details를 그대로 넣고, update도 `updateOperationInfo`에서 23개 값을 모두 대입한다. 따라서 새 details의 null은 기존 non-null을 덮는다. `upsert`와 `upsertBatch` 모두 동일하다.

제한 sync는 실행 내 `LinkedHashMap<externalId, candidate>`에서 마지막 **유효한** item을 선택한다. full sync는 페이지별 마지막 유효 item을 선택한 뒤 다음 페이지가 다시 갱신할 수 있다. 전체 필드가 null인 item은 skip되므로 이전 유효 item을 대체하지 않지만, baseTime만 있는 sparse item은 유효해서 앞선 요금/시간을 null로 대체할 수 있다. 이 동작을 H2 insert/update 및 duplicate 회귀 테스트로 고정했다.

| 정책 | 전제 | 이점 | 위험 |
|---|---|---|---|
| A. snapshot replace (현재) | 각 item이 완전한 최신 상태이고 null은 삭제/미상 상태를 나타냄 | provider가 값을 철회한 경우 반영 가능 | sparse/일시 결측/잘못된 포맷이 기존 정상값을 지울 수 있음 |
| B. non-null merge | null/누락은 미제공이며 기존 상태 유지가 맞음 | sparse 응답으로 인한 값 소실 완화 | 폐지된 요금/시간을 무기한 유지하고 서로 다른 시점의 필드를 합성; 의도적 삭제 표현 불가 |

현재 확보 문서에는 full snapshot/patch 구분, absent/null/blank 의미, 요금 폐지·운영시간 삭제 표현, 중복 item 순서·버전 보장, malformed 재전송 정책이 없다. **어느 정책이 더 맞는지 확정 불가하여 A를 유지한다.** 데이터 지우기나 non-null merge로의 production 정책 변경은 하지 않았다.

결정에 필요한 계약: (1) item 완전성, (2) 필드 absent/null/blank/0의 구별, (3) 명시적 삭제 표시, (4) 관측시각/버전과 중복 우선순위, (5) fee/time 형식·범위. 향후 승인된 제한 sync에서 아래 원인별 집계와 기존 non-null→incoming null 전이 수를 함께 관찰해야 한다. 전이 계측과 원인별 DB 이력 저장은 이번 범위에서 추가하지 않았다.

## 추가된 quality metric

`OperationQualityMetrics.inspect`는 기존 parser의 parseInteger/parseRange를 재사용하고 결과나 저장값은 바꾸지 않는다. 제한 sync와 full page 처리에서 `OPERATION_QUALITY`를 페이지당 한 줄 기록한다.

- `items`: raw DTO item 수. 식별자 누락, 중복, invalid, orphan도 포함하며 저장 행 수가 아님.
- `numericMissing`, `numericMalformed`: baseFreeTime/baseTime/baseFee/additionalTime/additionalFee/dailyMaxFee/monthlyFee 각각의 카운트.
- missing: nested 객체 부재, 필드 null, 빈 문자열, 공백을 합친 값. 현재 String DTO로 absent와 explicit null을 구분할 수 없어 따로 만들지 않음.
- malformed: nonblank 원문이 기존 정수 parser에서 null이 된 경우. "0"/"000000" 숫자는 정상이며 포함하지 않음.
- `timeMissing`, `timeMalformed`: 월–일/공휴일 총 16개 endpoint 단위 카운트.
- `midnightPairs`: 두 필드가 000000인 일자 pair 수. 이 pair를 timeMissing/malformed에 중복 계수하지 않음.
- 정상 자정 포함 범위는 오류로 세지 않음. `OperationQualityMetricsTest`에서 각 구분 및 중복 occurrence를 검증.
- 집계에는 raw 문자열, raw body, externalId, URI, serviceKey가 없으며 metric 이름은 고정됨.
- full-page 로그는 commit 전 관측 결과다. retry/rollback/동일 페이지 재처리 시 여러 줄이 생길 수 있어 jobId/pageNo와 실행 맥락 없이 단순 합산하면 안 된다. 성공 저장 통계는 기존 `OPERATION_PAGE_STATS`/job 통계와 구분한다.

이 metric은 다음 수집의 원인 파악을 돕지만 과거 NULL의 원인을 복원하지 못한다. 외부 API와 실제 sync는 이번에 실행하지 않았다.
