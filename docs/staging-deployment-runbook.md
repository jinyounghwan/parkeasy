# ParkEasy staging deployment runbook

> 실제 staging 대상 확인 결과와 A~L 실행 순서, 서버 변경 승인 범위는 [실행 리허설](staging-execution-rehearsal.md)에 정리했다. backend/frontend 분리 전환은 해당 문서와 템플릿을 사용한다.

> 2026-09-27 리허설의 최종 실행 기준은 [staging go-live checklist](staging-go-live-checklist.md)다. 아래 일반 migration/build 절차를 이번 작업에서 실행하지 않는다. 실제 migration·DB write·sync 금지 조건에 따라 기존 V13 DB가 필요하며, 시작 명령·nginx·smoke·rollback·sign-off는 최종 체크리스트를 따른다.

이 문서는 staging에서 ParkEasy를 검증하기 위한 절차다. production 배포, 전국 sync, 외부 공공 API 대량 호출, scheduler 활성화는 이 절차에 포함하지 않는다. 모든 명령은 실제 환경에 맞게 secret manager/배포 시스템에 연결하고, secret 값을 shell history·로그·문서에 넣지 않는다.

## 0. 판정 기준

- **Staging 가능:** 현재 코드와 산출물은 staging 검증을 시작할 수 있다.
- **Production 불가:** 실제 Git tracked 상태, 운영 DB backup/migration 승인, TLS/reverse proxy, secret manager, edge ACL은 이 workspace만으로 확인할 수 없다.
- staging은 prod profile과 read-only/최소 권한 DB 계정으로 먼저 검증한다. 관리 endpoint는 기본적으로 등록되지 않아야 한다.

## 1. 사전 체크

배포 담당자는 다음을 확인한다.

- [ ] `SPRING_PROFILES_ACTIVE=prod`가 단일 profile로 적용된다. local/test profile을 함께 활성화하지 않는다.
- [ ] Java 17과 지원되는 MySQL 버전을 준비한다.
- [ ] staging DB backup/snapshot과 복구 지점을 확보한다.
- [ ] staging DB schema가 Flyway migration 대상이며 애플리케이션 계정은 읽기/서비스에 필요한 최소 권한만 가진다.
- [ ] frontend 운영 도메인과 Kakao JavaScript key 허용 도메인을 등록한다.
- [ ] reverse proxy가 HTTPS를 종료하고 `/api/`만 backend로 전달한다.
- [ ] `/api/admin/**`와 legacy 전체목록 `/api/parking-lots`를 edge ACL 또는 internal route로 제한한다.
- [ ] `PARKEASY_CORS_ALLOWED_ORIGINS`는 정확한 `https://...` origin 목록이다. wildcard와 trailing path를 넣지 않는다.
- [ ] scheduler 세 값과 `parkeasy.admin.enabled=false`가 배포 manifest에 명시된다.

## 2. Required environment

backend `.env.example`에는 다음 이름만 기록되어 있다. 실제 값은 secret manager에서 주입한다.

| 변수 | 필요 여부 | 용도 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | 필수 | `prod` |
| `PARKEASY_DB_URL` | 필수 | JDBC URL |
| `PARKEASY_DB_USERNAME` | 필수 | 서비스 DB 사용자 |
| `PARKEASY_DB_PASSWORD` | 필수 | 서비스 DB 비밀번호 |
| `PARKEASY_PUBLIC_DATA_SERVICE_KEY` | 수집 maintenance에서만 | 공공데이터 API key; read-only web pod에는 불필요 |
| `PARKEASY_CORS_ALLOWED_ORIGINS` | 운영 권장 | 쉼표로 구분한 exact origin |
| `VITE_KAKAO_MAP_KEY` | frontend build 필수 | 브라우저용 Kakao JavaScript key |

`VITE_KAKAO_MAP_KEY`는 브라우저에 노출되는 public app key이므로 Kakao console에서 도메인을 제한한다. DB password와 public-data serviceKey를 `VITE_*` 변수에 넣지 않는다.

검증 명령은 값 대신 변수 존재 여부만 출력한다.

```powershell
$required = 'SPRING_PROFILES_ACTIVE','PARKEASY_DB_URL','PARKEASY_DB_USERNAME','PARKEASY_DB_PASSWORD','PARKEASY_CORS_ALLOWED_ORIGINS'
foreach ($name in $required) { if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) { throw "Missing environment variable: $name" } }
if ($env:SPRING_PROFILES_ACTIVE -ne 'prod') { throw 'Expected SPRING_PROFILES_ACTIVE=prod' }
```

## 3. Build and artifact check

Build in a clean CI workspace. Do not copy `.env.local`, local logs, database dumps, or `.smoke-tools` output into an image.

```powershell
cd backend
.\gradlew.bat --gradle-user-home D:/dev/parkeasy/.gradle-user-home clean test integrationTest build --console=plain
cd ..\frontend
npm.cmd ci
npm.cmd run lint
npm.cmd run build
```

Expected artifacts:

- `backend/build/libs/backend-0.0.1-SNAPSHOT.jar` — executable boot JAR. Do not deploy `*-plain.jar`.
- `frontend/dist/` — static assets served by the frontend web server.

Check existence and size without printing secrets:

```powershell
Get-Item backend/build/libs/backend-0.0.1-SNAPSHOT.jar
Get-ChildItem frontend/dist -File -Recurse
```

The current workspace has both artifacts. Build output and dependency caches remain deployment-excluded.

## 4. Database migration procedure

Current migration sequence is V1 through V13 under `backend/src/main/resources/db/migration`. The prod application sets `spring.flyway.enabled=false` intentionally; migration is a separate controlled step.

1. Confirm the release artifact and migration checksum in CI.
2. Take a tested staging DB backup/snapshot and record restore instructions.
3. Run Flyway using a migration-only identity or approved migration job against staging. Do not run the web JAR with `ddl-auto=update`.
4. Verify Flyway schema history, expected tables/indexes, and an application read-only `SELECT 1`.
5. Start the prod-profile web service with `spring.flyway.enabled=false` and `spring.jpa.hibernate.ddl-auto=none`.
6. If migration or smoke validation fails, stop the new service, restore the snapshot/backup according to the DB runbook, and redeploy the previous artifact. Do not improvise down migrations in production.

Migration is not executed by this workspace runbook. A backup and restore drill is required before production.

## 5. Backend start and proxy

Example service command (secrets are already in the process environment):

```powershell
java -jar backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod --server.forward-headers-strategy=framework
```

Recommended topology:

```text
HTTPS :443
  /        -> frontend static dist/
  /api/*   -> backend 127.0.0.1:8080
```

The reverse proxy must:

- redirect HTTP to HTTPS and send HSTS only after HTTPS is verified;
- preserve `Host`, `X-Forwarded-Proto`, `X-Forwarded-For`, and configure Spring `server.forward-headers-strategy=framework`;
- set finite connect/read/idle timeouts and request-size limits;
- avoid logging query strings, Authorization headers, request bodies, or service keys;
- restrict `/api/admin/**` and `/api/parking-lots` as separate edge routes;
- allow GET `/api/health/live` and `/api/health/ready` for the deployment probe;
- proxy only `/api/` to backend; Kakao browser SDK traffic is loaded by the frontend and is not a backend proxy route.

If frontend and backend use one origin, leave CORS origins empty. If they use separate origins, set exact HTTPS origins and verify preflight. CORS is not admin authentication.

## 6. Health/readiness verification

After the backend is ready:

```powershell
Invoke-WebRequest https://staging.example.test/api/health/live
Invoke-WebRequest https://staging.example.test/api/health/ready
```

Expected:

- `/api/health/live` → HTTP 200, `{"status":"UP"}`. This only means the HTTP process is answering; it does not verify the DB.
- `/api/health/ready` → HTTP 200, `{"status":"UP"}` after a read-only `SELECT 1` and rollback. HTTP 503 `{"status":"DOWN"}` means the DB connection/readiness check failed; do not send traffic to the instance.

Neither endpoint reports secrets, scheduler state, worker IDs, or provider freshness. Realtime freshness must be checked in the operator audit, not inferred from readiness.

## 7. Staging smoke test

Run after migration, backend readiness, frontend loading, and Kakao domain configuration. Record timestamp, release version, browser, and HTTP status without recording secret URLs.

- [ ] Open the staging HTTPS URL and confirm the map/search UI loads without console errors.
- [ ] Search for `서울역`; select a Kakao place result and confirm one nearby request is made.
- [ ] Confirm nearby returns at most 100 rows by default and map/list remain usable.
- [ ] Open a result detail. Confirm detail loads and its base fee/time matches the nearby card when operation data exists.
- [ ] Choose FEE sort. Confirm known operation base fees ascend and unknown fees appear last.
- [ ] Choose AVAILABLE sort. Confirm fresh availability precedes stale availability, stale rows show `업데이트 지연`, and missing realtime shows `실시간 정보 없음`.
- [ ] Confirm `available=0` renders `잔여 0면`; never label it “만차 확정”.
- [ ] Confirm an actual stale row renders both its number and `업데이트 지연`.
- [ ] Confirm 공영/민영/무료/24시간 controls are disabled and explain that classification data is unsupported. They must not silently return misleading zero-result searches.
- [ ] Confirm mobile viewport (390×844) has no horizontal overflow and detail panel stays inside the viewport.
- [ ] Confirm an API 5xx/timeout produces the existing loading/error state and does not expose stacktrace or configuration.
- [ ] Confirm `/api/admin/parking-sync/...` is 404 from the public staging origin.
- [ ] Confirm deprecated `GET /api/parking-lots` is blocked at the edge or restricted to an approved internal route. If temporarily reachable for compatibility, record the `Deprecation` response header and response-size risk.

No nationwide sync or provider refresh is part of this smoke test.

## 8. Rollback

Application rollback: remove traffic from the new instance, stop it, and redeploy the previously approved JAR/static bundle. Keep the database unchanged if the migration is backward-compatible.

Database rollback: stop application writes, use the recorded DB restore procedure/snapshot, verify Flyway history and application schema compatibility, then start the previous artifact. Do not issue ad-hoc destructive SQL. Record whether the migration was reversible; restoring a backup is the fallback for destructive or incompatible changes.

## 9. Current decision

**A — ready to start staging now:** artifacts, tests, prod configuration shape, CORS, health probes, admin default-off behavior, and smoke procedure are present.

**B — staging only at this point:** actual staging infrastructure, TLS, secret manager, reverse proxy, DB backup/migration job, edge ACL, and external consumer inventory are not available in this workspace.

**C — required before production:** run the checklist with real operators; validate ENV injection without value output, backup/restore, Flyway V1–V13, TLS/forwarded headers, admin/legacy edge blocking, Git secret history scan, health probe failure behavior, and the complete smoke test. Keep scheduler and admin sync disabled until a separate maintenance process is approved.
