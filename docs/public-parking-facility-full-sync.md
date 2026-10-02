# 전국 주차장 시설정보 초기 적재

이 기능은 `FACILITY_FULL` job을 페이지 단위로 처리한다. 기존 지역 시설 동기화와 Scheduler에는
연결되어 있지 않으며, 관리자 API를 명시적으로 호출할 때만 실행된다.

start/resume 요청은 job을 단일 전용 background executor에 제출하고 즉시 HTTP 202를 반환한다.
서버 시작 시 stale job을 자동 실행하지 않는다.

## 개발 환경 제한 실행

이 API에는 아직 인증이 없다. 로컬 개발 환경에서만 사용하고 운영 배포 전에는 관리자 인증과
endpoint 접근 제어를 반드시 추가한다.

3페이지만 새 job으로 실행:

```text
POST /api/admin/parking-sync/facilities/full/start?maxPages=3
```

중단되거나 제한에 도달한 최근 job을 최대 3페이지 재개:

```text
POST /api/admin/parking-sync/facilities/full/resume?maxPages=3
```

특정 기존 job은 `resume?jobId={id}&maxPages=3`처럼 ID를 지정해 안전하게 재개할 수 있다.

상태 조회와 page 경계 pause 요청:

```text
GET  /api/admin/parking-sync/facilities/full/status
POST /api/admin/parking-sync/facilities/full/pause
POST /api/admin/parking-sync/facilities/full/repair?jobId={id}
```

`maxPages`는 기본값이 없고 1~100만 허용된다. 무제한 전국 실행에는 두 개의 명시적 파라미터가
모두 필요하다.

```text
POST /api/admin/parking-sync/facilities/full/start?full=true&confirm=FACILITY_FULL_SYNC
```

## 처리 및 재개 정책

- 실행마다 새 job row를 생성하여 이력을 보존한다. resume은 가장 최근 `FAILED`/`PAUSED` job 또는
  프로세스 종료 후 DB에 남은 stale `RUNNING`/`READY` job을 재사용한다. 같은 JVM에서 실제 실행 중이면 lock이 먼저 차단한다.
- 외부 HTTP 호출은 DB transaction 밖에서 실행한다.
- 한 페이지의 batch upsert와 checkpoint 갱신은 하나의 짧은 transaction이다.
- 성공 전에는 `next_page`를 변경하지 않는다. page N 실패 시 `failed_page=N`, `next_page=N`이다.
- `totalPages` 이전의 empty/short page는 완료가 아니라 일시적 upstream 불완전 응답으로 처리한다.
- `COMPLETED`는 `lastSuccessPage >= totalPages`일 때만 허용한다. 마지막 page의 short 응답은 정상이다.
- timeout 및 502/503/504만 최대 한 번 재시도하며 기본 backoff는 2초다.
- client retry까지 실패하면 worker가 기본 60초 cooldown 후 같은 checkpoint page를 최대 5회 자동 재개한다.
- 파싱, 인증, 일반 4xx와 DB 오류는 worker 자동 재개 대상이 아니다.
- auto-resume 횟수와 연속 실패 횟수는 한 page가 commit되면 0으로 reset된다.
- `maxPages`는 DB의 `page_limit/successful_pages`로 관리하므로 auto-resume 후에도 성공 페이지 예산이 늘지 않는다.
- 페이지 간 기본 1초 delay는 provider 부하 방지용 pacing이며 timeout 해결책을 보장하지 않는다.
- pause는 실행 thread를 interrupt하지 않고 현재 페이지 commit 후 적용된다.
- lock은 단일 JVM에서만 유효하다. 다중 인스턴스 운영에는 분산 lock이 추가로 필요하다.

설정:

```properties
parkeasy.public-data.full-sync.facility.num-of-rows=1000
parkeasy.public-data.full-sync.facility.page-delay-ms=1000
parkeasy.public-data.retry-backoff-ms=2000
parkeasy.public-data.full-sync.facility.auto-resume-enabled=true
parkeasy.public-data.full-sync.facility.auto-resume-delay-ms=60000
parkeasy.public-data.full-sync.facility.max-auto-resume-attempts=5
```

실패는 공통 `PublicDataFailureClassifier`에서 네트워크 일시 오류, 502/503/504, 영구 파싱 오류,
영구 client 오류와 unknown으로 분류한다. manual resume은 현재 page의 자동 재개 횟수와 연속 실패
횟수를 0으로 reset하지만 마지막 실패 시각과 분류 정보는 보존한다.

`num-of-rows`는 1~1000으로 검증하며 기본값은 실제 응답이 확인된 1000이다. page size는 job 생성 시
`parking_sync_job.num_of_rows`에 고정된다. 설정을 바꿔도 기존 job의 checkpoint와 page size는 변경되지 않는다.

100건/page로 진행한 기존 job은 PAUSED 상태로 보존하고 1000건/page 새 job을 page 1부터 시작한다.
기존 parking lot은 `(data_source, external_id)` 기준으로 update되므로 삭제하거나 이전할 필요가 없다.

## 중복 집계

`duplicate_count`는 같은 API page 안에서 유효한 동일 `externalId`가 두 번째 이후 등장한 횟수다.
누락/invalid externalId는 skipped에만 포함되고 duplicate와 중복 집계하지 않는다. 이전 page 또는 DB에
이미 존재한 externalId는 duplicate가 아니라 updated다. 따라서 각 page에서 다음 관계가 성립한다.

```text
raw = inserted + updated + skipped + duplicateOccurrences
```

INFO `FACILITY_PAGE_STATS`는 raw/unique/invalid/duplicate 집계를 남기고, DEBUG
`FACILITY_DUPLICATE`는 page당 최대 5개 externalId만 남긴다.

## 예상 시간 계산

```text
totalPages = ceil(totalCount / numOfRows)
estimatedSeconds = totalPages * (평균 API 응답 초 + page delay 초 + 평균 DB page 처리 초)
```

예를 들어 1,767,934건, 1000건/page이면 1,768페이지다. 평균 API 0.7초, pacing 1초,
DB 처리 0.3초를 가정하면 약 3,536초(약 59분)다. timeout과 retry가 발생하면 더 길어진다.
