# P1 운영 품질 및 배포 준비 점검

관련 분석: [operation 매핑·null 정책·quality metric](p1-operation-mapping-2026-09-22.md), [P0 정합성 결과](p0-data-consistency-2026-09-22.md).

이번 작업에서 실제 MySQL은 read-only SELECT만 수행했다. 전국/제한 외부 공공 API 호출, 실제 sync, scheduler 활성화, backfill, UPDATE/DELETE, migration은 실행하지 않았다. 테스트의 sync는 가짜 client와 H2 fixture만 사용한다.

## 1. operation null overwrite와 계측

현재는 snapshot replace다. insert/update 모두 incoming details를 그대로 저장하고 null도 기존 non-null을 덮는다. 동일 범위의 중복은 마지막 유효 item을 선택하므로 일부 필드만 있는 sparse item도 앞의 풍부한 item을 대체할 수 있다. 완전 결측 item은 skip한다.

provider가 full snapshot인지 patch인지, null이 삭제인지 미제공인지 알려진 계약이 없다. non-null merge로 바꾸면 폐지된 요금과 운영시간을 계속 표시할 위험이 있어 **production 저장/파싱 정책을 변경하지 않았다**. 양쪽 위험과 필요한 계약은 별도 mapping 문서에 비교했다.

`OperationQualityMetrics`를 추가했다. 기존 parser로 확인 가능한 숫자별 missing/malformed, 시간 endpoint missing/malformed, midnight pair를 페이지별로 집계한다. raw body·원본 문자열·식별자·키는 로그에 넣지 않는다. DTO에서 구별되지 않는 absent/null/blank는 missing에 합친다. retry 시 중복 관측 로그가 생기므로 저장 통계나 고유 행 수로 해석하면 안 된다. 과거 NULL 원인은 이 metric으로 소급 복원할 수 없다.

## 2. realtime 현재 상태

[실제 SELECT 결과](../.smoke-tools/p1-db-final.txt), DB 시각 **2026-09-23 06:14:04.941140** 기준. 판정은 `received_at < NOW() - INTERVAL 30 MINUTE`이며 null timestamp도 stale로 간주한다. 애플리케이션의 isBefore 정책과 동일하다.

| total | stale=true 예상 | stale=false 예상 | MIN(received_at) | MAX(received_at) |
|---:|---:|---:|---|---|
| 195 | 195 | 0 | 2026-09-08 20:51:07 | 2026-09-08 20:51:07 |

DB session time zone은 SYSTEM이고 현재 로컬 시스템은 한국 시간이다. 배포 시 JVM/DB 시간대를 일치시켜야 한다. DATETIME/LocalDateTime은 자체 timezone을 담지 않는다. 이번처럼 수일 오래된 데이터는 30분 경계나 timezone 차이로 fresh가 되지 않는다.

UI 정책은 그대로다:

| 상태 | 표시 |
|---|---|
| fresh | 잔여 N면 |
| stale | 잔여 N면 + 업데이트 지연 |
| realtime/available 정보 null | 실시간 정보 없음 |
| available=0 | 잔여 0면; 만차 확정으로 표현하지 않음 |

AVAILABLE 정렬은 fresh → stale → 잔여값 미상 그룹 순서, 그룹 내 잔여면수 내림차순, 동률 거리순이다. 기존 1,000,000 차감 방식은 매우 큰 stale 수치가 fresh보다 앞설 수 있어 그룹 comparator로 최소 수정했다. 1,500,000 stale과 fresh 0을 비교하는 회귀 테스트로 정책을 검증했다. 실제 시설/실시간 총면수는 계속 분리한다.

## 3. realtime 운영 전략

- 현재 client 계약은 serviceKey/numOfRows/pageNo/format만 제공한다. externalId target 및 provider 지역 필터의 근거가 없다. 응답에도 지역 선택에 필요한 필드가 없다.
- 약 **788페이지**는 기존 조사에서 기록된 전국 realtime scan 규모이며 이번 외부 재조회로 갱신한 값은 아니다.
- recurring nationwide refresh로 모든 대상의 30분 freshness를 보장할 수 없다. 페이지 지연·전송·timeout·재시도 누적으로 선행 페이지가 완료 전에 stale이 될 수 있다.
- `receivedAt`을 job 종료시각이나 현재시각으로 갱신해 freshness를 꾸미지 않는다.
- realtime scheduler는 기본 OFF. 기존 scheduler를 켜면 target refresh가 아니라 **전국 목록의 앞쪽 페이지**를 반복 호출한다. 기본 max-pages=1, 페이지당 10건이며 자동으로 전국 788페이지를 모두 호출하는 구조는 아니다. 제한을 늘리거나 별도 전국 순회를 반복하면 비효율적 scan과 timeout 압력이 커진다.
- `REALTIME_FULL`은 필요 시 별도 승인된 수동/저주기 coverage 보정 용도다. 자동 부팅/10분 전국 주기를 도입하지 않는다.
- target/지역 query가 공식 확인되거나 다른 feed가 확보되기 전에는 stale 표시를 유지한다. nearby GET으로 provider를 호출하거나 background refresh를 시작하지 않는다.
- 장기적으로 제한 대상 refresh + 낮은 빈도의 coverage 보정을 검토하되, 공급자 계약·쿼터·측정된 처리시간·중복 실행 제어가 선행되어야 한다.

## 4. legacy 전체목록 API

`GET /api/parking-lots`
→ `ParkingLotController.findAll`
→ `ParkingLotService.findAll`
→ JpaRepository `ParkingLotRepository.findAll`
→ `ParkingLotSummaryResponse.from(ParkingLot)`.

pagination/limit가 없고 전체 테이블을 읽어 List로 반환한다. operation/realtime 조회는 없으며 parking_lot의 baseFee/baseTime/분류 boolean 요약을 사용한다. canonical operation과 drift할 수 있고, 현재 규모 약 267,369 시설 전체 로딩·직렬화는 메모리/응답 크기 및 공개 요청 반복 위험이 있다.

frontend `parkingApi.ts`의 호출은 nearby와 /{id} 두 경로뿐이다. 저장소 코드에서 외부 consumer를 찾지 못했지만 저장소 밖 consumer가 없다는 보장은 아니다. 선택은 **A: deprecated 표시하고 호환성 유지**다. controller `@Deprecated(forRemoval=false)`와 HTTP `Deprecation: @1790035200`을 추가했고 응답 구조/상태/정렬/전체 반환 의미는 바꾸지 않았다. 실제 MySQL 전체목록 GET은 이번에 실행하지 않았다.

운영 신규 consumer는 nearby/detail을 사용한다. 공개 배포 전 edge에서 정확히 `/api/parking-lots` 전체목록 경로(끝 slash 변형 포함)를 차단하거나, 기존 consumer가 필요하다면 internal allowlist/rate limit로 제한해야 한다. nearby/detail prefix까지 차단하지 않도록 경로를 구분한다. 호환성이 정리된 뒤 pagination/제거를 별도 결정한다.

## 5. scheduler와 full sync 자동 시작

| 설정 | default | local | prod | H2 integration profiles |
|---|---|---|---|---|
| facility.enabled | false | default 상속 | false | false |
| operation.enabled | false | default 상속 | false | false |
| realtime.enabled | false | default 상속 | false | false |

`application*.properties`를 확인했다. unit test 설정은 DataSource 자동 설정을 제외하며 scheduler 실행 테스트는 mock 기반이다. 각 scheduled callback은 enabled=false이면 sync를 호출하기 전에 반환한다.

full sync를 자동 시작하는 ApplicationRunner/CommandLineRunner/PostConstruct/ApplicationReady listener는 없다. start/resume API 호출로 worker가 시작된다. `auto-resume-enabled=true`는 **수동 시작된 job 내부에서 일시 장애가 난 경우의 복구**이며 scheduler 활성화나 재부팅 시 과거 job 자동 재개가 아니다.

환경변수/명령행 override가 파일 기본값보다 우선하므로 실제 배포 manifest에도 세 enabled=false를 명시하고 확인한다. 이번에는 실제 scheduler/full sync를 실행하지 않았다.

## 6. secret/config audit

실제 값은 출력하지 않고 존재 방식만 확인했다.

| 대상 | 현재 보관 방식 | Git 관련 판단 |
|---|---|---|
| local DB password | PARKEASY_DB_PASSWORD ENV placeholder, literal fallback 없음 | application-local.properties는 ignore 대상이 아님; 현재 비밀번호 literal은 없음 |
| serviceKey | PARKEASY_PUBLIC_DATA_SERVICE_KEY ENV placeholder, 기본값 빈 값 | 설정/Java는 placeholder, 실제 키 하드코딩 없음 |
| prod DB URL/user/password | 각각 PARKEASY_DB_URL / PARKEASY_DB_USERNAME / PARKEASY_DB_PASSWORD 필수 ENV | 새 prod 설정에 literal credential 없음 |
| Kakao JavaScript key | frontend/.env.local의 VITE_KAKAO_MAP_KEY, 빌드 시 VITE ENV 사용 | .env.local은 .env.* 및 *.local ignore 규칙 대상 |
| 기타 secret/token | 검사한 소스/설정에서 실제 추가 secret literal 발견 안 됨 | 테스트의 dummy marker는 실제 credential 아님 |

이 작업 폴더의 root/backend/frontend에 `.git` 메타데이터가 없어 **실제 tracked 여부와 Git 이력은 확인 불가**다. ignore 규칙이 있어도 이미 tracked인 파일을 untrack하지는 않는다. 실제 저장소에서 `git ls-files`/secret scan과 이력 확인을 배포 전 해야 한다.

root ignore는 `.env`, `.env.*`를 제외하고 `!.env.example`만 허용한다. backend/frontend `.env.example`을 실제 값 없이 추가했다. Spring Boot가 `.env`를 자동 로딩한다고 가정하지 말고 deployment secret/ENV로 주입한다.

Kakao VITE 키는 브라우저 bundle에 노출되는 JavaScript app key다. 서버 API secret을 VITE 변수에 넣으면 안 되며 Kakao의 허용 web domain을 개발/운영 도메인으로 제한한다. 실제 키/토큰을 새 문서, 커맨드라인 인자, 빌드 로그에 넣지 않는다.

## 7. CORS

기존에는 backend CORS 허용 설정이 없고 frontend 개발 서버 proxy를 통한 same-origin 구조였다. wildcard 허용을 발견한 것은 아니다.

최소 외부화: `ParkingWebConfiguration`, `parkeasy.cors.allowed-origins=${PARKEASY_CORS_ALLOWED_ORIGINS:}`.

- default/prod 빈 목록: same-origin만 사용; 임의 cross-origin 허용 없음.
- local 기본값: http://localhost:5173, http://127.0.0.1:5173.
- 운영 ENV는 comma-separated 정확한 origin만 허용. path/query/userinfo/fragment/wildcard는 시작 시 거부하며 잘못된 입력값 자체는 오류문에 출력하지 않는다.
- `/api/parking-lots/**`, GET만 CORS 허용; credentials=false. admin endpoint에는 CORS 허용을 추가하지 않았다.
- CORS는 서버 간 요청이나 curl을 막는 인증 기능이 아니다. admin 보호를 대체하지 않는다.

## 8. admin endpoint 보호 선택

기존 `/api/admin/parking-sync/**` 및 `/api/admin/public-data/diagnostic/**`에는 Spring Security/인증/인가가 없었다. 공개 노출 시 무인증 외부 API 호출 및 DB 쓰기/full-sync 실행이 가능해 **배포 차단급 위험**이다.

| 대안 | 판단 |
|---|---|
| A. prod profile에서 미등록 | **선택**. public read 서비스에 관리 API가 필요 없고 의존성 추가가 없음 |
| B. 별도 secret/header | TLS/회전/비교/로그/권한 경계 관리가 필요해 이번에는 추가하지 않음 |
| C. Spring Security ADMIN role | 장기적인 다중 운영자 감사/인가에 적합; 이번 범위에서 큰 인증 시스템 추가하지 않음 |
| D. internal network only | A와 함께 maintenance 환경에 적용할 운영 조건. 네트워크 제한만 코드로 보장한다고 주장하지 않음 |

관리 controller 7개 모두 `parkeasy.admin.enabled=true`일 때만 등록하고 `@Profile("!prod")`를 추가했다. 기본 false이며 prod에서는 enabled=true를 주더라도 등록되지 않는다. 기본/명시 OFF/prod 강제 ON 조합을 테스트했다. 이 조치는 인증 구현이 아니라 **endpoint 제거를 통한 노출 차단**이다.

운영 read 서비스는 prod로 실행하며 edge에서도 `/api/admin/**`를 차단한다. maintenance가 필요하면 별도의 private 인스턴스/네트워크 및 운영자 접근 통제를 사용하고 그 환경에서만 명시적으로 opt-in한다. opt-in을 주는 것만으로 sync가 시작되지는 않는다. 이번에 실제 관리 API로 sync를 호출하지 않았다.

## 9. error/log 및 Actuator

- Actuator dependency/endpoint 없음. env/configprops/heapdump 등을 공개하지 않았다.
- `server.error.include-stacktrace=never`, include-message=never, include-binding-errors=never, include-exception=false를 명시했다.
- `spring.mvc.log-request-details=false`. prod에서는 Hibernate SQL/bind 로그 OFF, HTTP client 로그 INFO. Basic/Auth header를 로깅하는 custom filter/interceptor는 없다.
- public-data caller 및 full-sync 로그는 page/job/count/status/error type 중심이다. raw body/URI/serviceKey/credential을 직접 기록하는 코드를 발견하지 않았다. 기존 dummy-key 비노출 로그 테스트도 유지했다.
- 잠재 위험: client 예외에는 request URI(serviceKey query), provider resultMsg, Jackson raw 문맥이 포함될 수 있다. admin controller advice에서 RestClientException, IllegalStateException, IllegalArgumentException을 고정된 안전한 response로 처리하고 예외 객체를 로그로 출력하지 않는다. full-sync controller의 exception.getMessage 응답도 제거했다. status 400/500/502/504 비밀 marker 비노출 테스트를 추가했다.
- readiness 오류도 DB 예외/URL/credential을 응답하거나 로그에 넣지 않는다.
- 제3자 DEBUG/TRACE, JVM HTTP debug, reverse proxy의 header/query/body logging을 켜면 이 보장을 깨뜨릴 수 있다. 운영 logging override와 수집 파이프라인 redaction은 별도 확인해야 한다.
- provider endpoint 기본 URL은 현재 HTTP이다. 이번에 외부 호출 없이 HTTPS 지원 여부를 새로 확정하지 않았다. prod에서는 admin/scheduler를 OFF로 두어 사용하지 않고, 향후 maintenance 수집 전에 공식 HTTPS 계약 또는 보호된 egress를 확인해야 한다. URL의 serviceKey를 평문 전송하는 문제를 무시하지 않는다.

## 10. health/readiness와 worker 상태

대규모 플랫폼/Actuator를 추가하는 대신 아래 최소 endpoint를 추가했다:

| endpoint | 의미 | DB/정보 노출 |
|---|---|---|
| GET /api/health/live | backend HTTP handler 동작 | DB 접근 없음, status=UP만 반환 |
| GET /api/health/ready | DB SELECT 가능 | readOnly + autoCommit=false + SELECT 1 + finally rollback, 성공 200 UP/실패 503 DOWN |

readiness statement timeout 2초, prod Hikari connection timeout 3초/validation timeout 1초. DB 예외 문자열, scheduler 구성, job ID를 public probe에 노출하지 않는다. readiness는 schema migration 완전성이나 provider freshness를 보증하지 않으므로 Flyway 검증 및 실제 nearby/detail smoke는 별도 단계다.

full-sync worker는 기존 private maintenance의 `/api/admin/parking-sync/{facilities|operations|realtime}/full/status`에서 job 상태와 process running 값을 볼 수 있다. 조회는 sync를 시작하지 않지만 기본/prod에는 해당 endpoint가 없다. scheduler 활성 상태는 배포 ENV/config와 startup 운영 점검으로 확인한다. public health에 내부 상태나 인증 없는 worker 관리 경로를 추가하지 않았다. 다중 인스턴스 worker 상태와 private metrics endpoint는 후속 항목이다.

## 11. Production 체크리스트

- [ ] `SPRING_PROFILES_ACTIVE=prod`만 적용. local profile을 함께 활성화하지 않기.
- [ ] DB URL/user/password를 ENV/secret manager로 주입. 읽기 서비스에는 최소 DB 권한, migration에는 분리된 계정 사용.
- [ ] DB backup/restore 및 Flyway migration 버전/checksum을 staging에서 검증. `application-prod.properties`는 startup Flyway OFF이므로 **배포 pipeline의 별도 승인 migration 단계**를 준비한 뒤 앱을 시작. 이번에 migration 실행하지 않음.
- [ ] public-data serviceKey는 필요한 private maintenance 환경의 ENV에만 주입. read-only 배포에서는 빈 값으로도 read API 이용 가능.
- [ ] Kakao JavaScript 키는 frontend 빌드 ENV에 주입하고 운영 도메인 제한 확인. 서버용 secret은 VITE에 넣지 않기.
- [ ] 운영 HTTPS와 reverse proxy, 정확한 CORS origins(또는 same-origin) 설정.
- [ ] facility/operation/realtime enabled=false를 배포 manifest에서 확인. full-sync 자동 시작 script/job/cron이 없는지 확인.
- [ ] prod의 admin endpoint 미등록과 edge `/api/admin/**` 차단 확인. private maintenance 접근 통제 별도 설정.
- [ ] legacy 전체목록 `/api/parking-lots`는 공개 edge 차단/내부 제한. nearby/detail은 정상 통과하는지 확인.
- [ ] SQL/bind/HTTP debug, header/query/body logging 비활성 확인. secret scan 및 실제 Git tracked/history 확인.
- [ ] JVM/DB timezone 일치, realtime 195행 전부 stale이라는 현재 한계 고지. 실시간 갱신 SLA가 있는 것처럼 홍보하지 않기.
- [ ] `/api/health/live`, `/api/health/ready` probe와 DB 장애 시 503 동작, staging nearby/detail GET 확인. 주기/timeout은 DB 연결 풀 부담을 고려해 설정.
- [ ] provider 수집을 재개하기 전에 snapshot/patch/null 계약, HTTPS·쿼터·timeout 정책과 raw 없는 quality 로그 관측 계획 확정.

## 12. 변경 파일과 검증

핵심 변경:

- operation: `OperationQualityMetrics`, 제한 sync/full-page 집계 연결, synthetic fixture/mapper 테스트, H2 persistence/overwrite 테스트. parser/entity 저장 정책은 유지.
- API: `ParkingLotController` deprecated header, `ParkingLotService` AVAILABLE 그룹 정렬, admin controller 7개 조건부 등록, `PublicDataApiExceptionHandler` 및 full controller error response.
- 운영: `ParkingWebConfiguration`, `HealthController`, application 기본/local/prod 설정, backend/frontend `.env.example`.
- 테스트: config/health/admin 등록/error 비노출/운영 endpoint 통합 검증. 기존 local MySQL에 쓰던 fixture 테스트 4개를 H2 profile로 변경. `LocalMySqlConnectionIntegrationTest`는 `PARKEASY_MYSQL_TEST_ENABLED=true` opt-in과 read-only/Flyway OFF 적용.
- build: integrationTest에서 synthetic fixture resource 재사용.
- 문서: 이 문서와 operation mapping 문서.

최종 검증:

- 지정된 `clean test integrationTest build --console=plain`: **BUILD SUCCESSFUL**.
- 단위 테스트 **118/118 통과**. 통합 테스트 **16 통과, 1 skip**, 실패/오류 0. skip은 ENV opt-in이 없는 `LocalMySqlConnectionIntegrationTest`이며 실제 DB freshness는 별도 read-only JDBC audit으로 확인했다.
- frontend `npm run lint`, `npm run build` 통과. UI 동작 코드는 이번 P1에서 변경하지 않았고 `.env.example`만 추가했다.
- [실제 prod runtime 검증](../.smoke-tools/p1-runtime.json): 2026-09-23 06:21 KST, 로컬 MySQL read-only, GET만 사용. live/ready 200, admin full-status 3종 및 diagnostic 경로 404, 허용 CORS origin 200/비허용 403. id=141 baseFee=3000/baseTime=30/stale=true 유지. 이 검증의 Java child(PID 32136)는 종료되어 build JAR 잠금을 남기지 않았다. 실제 배포 인프라를 검증한 것은 아니다.
- Gradle clean의 첫 실패는 이전 read-only 검증 서버의 JAR 잠금이었다. PID 31284 및 실행 명령의 ParkEasy JAR/read-only 옵션을 확인한 후 그 프로세스만 종료했다. 다른 Java 프로세스는 종료하지 않았다.

## 13. 배포 준비 판단

**배포 준비/staging 단계로 진행 가능하다. 공개 운영 배포 완료 판정은 아직 아니다.** 코드의 admin 노출 기본값과 CORS/error/health 기반은 마련했으나 실제 배포 환경, Git 이력, credential, migration pipeline, edge 차단은 이 workspace만으로 검증할 수 없다.

A. 배포 전 필수: 위 checklist의 profile/ENV·키/DB 권한·Flyway·TLS/CORS·admin/legacy edge 차단·secret scan·health/staging GET 확인.

B. 남은 P1: provider full snapshot/patch/null/삭제 계약 확정, 다음 승인 수집에서 quality metric 관찰 및 non-null→null 전이 원인 분석, target/지역 realtime feed 확보와 refresh SLA 결정, legacy consumer 확인 후 정식 전환 계획.

C. P2: legacy pagination/폐기·요약 컬럼 정리, private metrics와 다중 worker 관측, 부하/복구 자동화, malformed 원인별 세분화·수집 이력 보존 정책.
