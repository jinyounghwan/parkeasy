# 공공 주차장 시설정보 수동 동기화

이 기능은 한국교통안전공단 주차장 시설정보를 제한된 페이지 범위에서 조회해
`parking_lot`에 저장하는 개발 검증용 기능이다. 운영 환경에 배포하기 전
`/api/admin/parking-sync/**`에 인증·인가를 적용하거나 엔드포인트를 제거해야 한다.

## 환경변수

PowerShell 세션에서 다음과 같이 설정한다. 값은 소스나 로그에 기록하지 않는다.

```powershell
$env:PARKEASY_PUBLIC_DATA_SERVICE_KEY = "발급받은 인증키"
```

`application-local.properties`는 `${PARKEASY_PUBLIC_DATA_SERVICE_KEY:}`로 참조한다.

## 수동 API

```http
POST /api/admin/parking-sync/facilities?sido=경기도&sigungu=수원&maxPages=5
```

- `sido`, `sigungu`: 필수
- `maxPages`: 선택, 기본 5, 허용 범위 1~100
- API 호출의 `numOfRows`: 10으로 고정
- 외부 API가 마지막 페이지임을 나타내거나 빈 페이지를 반환하면 조기 종료

응답의 `requestedPages`는 요청한 상한이며 `fetchedCount`, `matchedCount`,
`insertedCount`, `updatedCount`, `skippedCount`, `duplicateCount`를 함께 반환한다.

`sido`는 공백 제거 후 정확히 일치해야 한다. `sigungu`는 공백 제거 후 양방향
접두어 비교를 사용하므로 요청값 `수원` 또는 `수원시`가 API 값
`수원시 팔달구`와 일치한다. 문자열 중간 포함 비교는 사용하지 않는다.

지역에 일치한 데이터 중 식별자·이름이 없거나 위도/경도가 숫자가 아니거나
지리 좌표 범위를 벗어나면 건너뛴다. 수용 면수 변환 실패는 `null`로 저장한다.
동일 실행에서 같은 `externalId`가 반복되면 마지막 유효 데이터를 사용한다.

DB 조회 키는 `(data_source=PUBLIC_DATA_PORTAL, external_id)`이다. 기존 행은
시설정보 도메인 메서드로 갱신하고, 없는 행은 삽입한다.

## 테스트

```powershell
cd backend
./gradlew.bat test
./gradlew.bat integrationTest
./gradlew.bat build
./gradlew.bat liveApiTest
```

`liveApiTest`는 일반 빌드와 분리되어 있으며 환경변수가 없으면 skip된다.
환경변수가 있더라도 정확히 첫 페이지 10건만 요청한다.

## 운영정보 수동 동기화

```http
POST /api/admin/parking-sync/operations?maxPages=1
```

- 기본 `maxPages=1`, 허용 범위 1~100, 페이지당 10건
- `prk_center_id`와 `parking_lot`의 `(PUBLIC_DATA_PORTAL, external_id)`가 일치할 때만 저장
- 동일 실행의 중복 식별자는 마지막 유효 데이터를 사용
- `000000~000000` 시간 쌍은 24시간으로 판단하지 않고 시작/종료를 모두 `null`로 저장
- 운영정보가 없는 주차장의 상세 응답은 `operation: null`

운영정보는 `parking_operation`에 주차장당 최대 한 행으로 저장된다. 수동 API는
시설정보 API와 마찬가지로 개발용이며 운영 배포 전 반드시 보호하거나 제거해야 한다.

## 실시간정보 수동 동기화

```http
POST /api/admin/parking-sync/realtime?maxPages=1
```

- 기본 `maxPages=1`, 페이지당 10건
- `(PUBLIC_DATA_PORTAL, external_id)`와 `prk_center_id`가 일치할 때만 저장
- 음수 수치 또는 `availableSpaces > totalSpaces` 레코드는 전체 skip
- `availableSpaces=0`은 정상값
- 원본 관측시각이 없어 `observedAt=null`, 서버 수신시각은 `receivedAt`에 기록
- timeout 및 HTTP 502/503/504만 최대 한 번 재시도

상세 응답에는 nullable `realtime`이, nearby 응답에는 nullable
`availableSpaces`가 추가된다. Nearby 실시간 상태는 주차장 ID 목록으로 한 번에 조회한다.

## 자동 동기화 Scheduler

기본값은 시설정보 매일 03:00, 운영정보 매일 03:30, 실시간정보 10분
간격이며 모두 `max-pages=1`이다. 실시간 첫 실행도 애플리케이션 시작 10분 후다.
각 작업은 독립적인 프로세스 내부 lock을 사용하고, 실패 시 Scheduler가 별도로
재시도하지 않는다.

```properties
parkeasy.sync.facility.enabled=true
parkeasy.sync.facility.cron=0 0 3 * * *
parkeasy.sync.facility.max-pages=1
parkeasy.sync.facility.sido=서울특별시
parkeasy.sync.facility.sigungu=강북구
parkeasy.sync.operation.enabled=true
parkeasy.sync.operation.cron=0 30 3 * * *
parkeasy.sync.operation.max-pages=1
parkeasy.sync.realtime.enabled=true
parkeasy.sync.realtime.fixed-delay=600000
parkeasy.sync.realtime.initial-delay=600000
parkeasy.sync.realtime.max-pages=1
parkeasy.sync.realtime.stale-after-minutes=30
```

환경변수나 프로필별 설정으로 값을 덮어쓸 수 있다. 모두 끄려면 각 `enabled`를
`false`로 설정한다. `receivedAt`이 stale 기준보다 오래되면 상세와 nearby
응답의 `stale`이 `true`가 된다.

## externalId 단건 시설 동기화

```http
POST /api/admin/parking-sync/facilities/by-external-id
    ?externalId=00000-19199-00004-00-1
    &maxPages=20
    &numOfRows=100
```

각 페이지에서 `prk_center_id`를 exact match하고 발견된 페이지까지만 조회한다.
같은 페이지의 중복은 마지막 유효 데이터를 사용하며, exact match가 모두 invalid면
추가 페이지로 넘어가지 않고 실패 사유를 반환한다. 기본 제한은 20페이지·100건,
각 허용 상한은 100이다. timeout이나 일시적 외부 장애는 공통 client에서 한 번만
재시도하며 최종 실패는 HTTP 504/502로 전달된다.
