# Staging 실행 리허설 — 2026-09-27

현재 판정: **NO-GO — 실제 staging 대상 정보 미확인**. 이 문서는 실행 준비와 로컬 정적 검증 결과다. SSH 접속, 업로드, 서비스 시작/재시작, nginx reload, DB 접속은 수행하지 않았다. production 배포, migration, DB write 테스트, scheduler 활성화, 전국/full sync, secret 출력은 금지한다.

기존 [go-live checklist](staging-go-live-checklist.md)의 API/UI/security 판정 기준을 유지한다. 이번 실행에서는 아래 A~L 순서와 분리된 backend/frontend symlink를 사용한다. 기존 문서의 단일 `current` 전환 명령과 혼용하지 않는다. 모든 서버 명령은 **승인 후 Linux Bash에서 실행할 예시**이며 현재 실행된 명령이 아니다.

## 1. 인프라 조사 결과와 필요한 정보

workspace의 배포 관련 문서·설정·ENV 파일과 현재 프로세스의 관련 ENV를 값 출력 없이 검사했다. 사용자 홈 SSH 설정이나 외부 secret manager는 조사하지 않았다. 발견하지 못했다는 것은 이 조사 범위의 결과이며 인프라 자체가 없다는 뜻은 아니다.

| 항목 | 확인 결과 | 배포 전에 제공할 정보 |
|---|---|---|
| staging hostname/IP | 실제 대상 미발견 | host/IP, staging 격리 확인 |
| SSH 배포 대상 | 미발견 | SSH user/port, bastion 여부, 인증 참조, host key fingerprint |
| nginx 설치 대상 | 미발견 | 설치 host, nginx 버전, config include 경로 |
| staging DB endpoint | 미발견; 로컬 DB 설정을 staging으로 사용하지 않음 | endpoint/port/database, 읽기 전용 자격증명 참조, TLS 요구 |
| backend deploy path | `/srv/parkeasy/...` 문서 예시만 존재 | 서버에서 승인된 releases 및 backend symlink 경로 |
| frontend deploy path | 문서 예시만 존재 | releases 및 frontend symlink 경로 |
| service name | `parkeasy-staging` 예시만 존재 | 실제 unit 이름, 실행 OS user/group |
| domain/TLS | `.invalid` 예시만 존재 | 실제 domain/DNS, 인증서·키 경로 또는 발급/갱신 방식 |
| backup/이전 release | workspace에서 서버 증적 미확인 | backup ID/시각/복구 증적, 이전 JAR/dist/config |

현재 프로세스에는 `PARKEASY_DB_PASSWORD`만 관련 DB ENV로 존재한다. URL/username이 없으며 password가 staging용인지 미확인이다. frontend `.env.local`에는 `VITE_KAKAO_MAP_KEY`가 있지만 staging domain 허용 여부와 dist build provenance는 미확인이다. 값을 읽어 출력하거나 다른 환경에 재사용하지 않는다.

필수 ENV 이름: `SPRING_PROFILES_ACTIVE`, `PARKEASY_DB_URL`, `PARKEASY_DB_USERNAME`, `PARKEASY_DB_PASSWORD`, `PARKEASY_CORS_ALLOWED_ORIGINS`. frontend build: `VITE_KAKAO_MAP_KEY`. `PARKEASY_PUBLIC_DATA_SERVICE_KEY`는 read-only 웹 기동에 불필요하며 이번에는 수집을 수행하지 않는다. secret은 채팅/CLI 인자 대신 승인된 secret manager 또는 보호 파일로 전달한다.

## 2. DB V13 read-only gate

접속 정보가 없어 아래 SQL은 **미실행**이다. 서버/DB 담당자가 대상이 staging인지 먼저 확인한다. MySQL client 전용 설정은 root 또는 작업자 소유 0600인 `/run/secrets/parkeasy-mysql-readonly.cnf`에 준비하고 database 및 TLS 검증 옵션을 포함한다. 앱 ENV 파일과 MySQL client 파일은 형식이 다르다. 해당 계정은 SELECT만 허용한다.

```bash
set +x
set -euo pipefail
umask 077
EVIDENCE="$HOME/parkeasy-private-evidence"
mkdir -p "$EVIDENCE"
MYSQL_CNF=/run/secrets/parkeasy-mysql-readonly.cnf
exists=$(mysql --defaults-extra-file="$MYSQL_CNF" --batch --skip-column-names \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='flyway_schema_history';" \
  2> "$EVIDENCE/db-errors.log")
test "$exists" = 1 || { echo 'STOP missing history'; exit 1; }
mysql --defaults-extra-file="$MYSQL_CNF" --batch \
  -e "SELECT installed_rank,version,description,type,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank;" \
  > "$EVIDENCE/schema-history.tsv" 2> "$EVIDENCE/db-errors.log"
gate=$(mysql --defaults-extra-file="$MYSQL_CNF" --batch --skip-column-names \
  -e "SELECT IF(COUNT(*)=13 AND COUNT(DISTINCT version)=13 AND SUM(success=1)=13 AND SUM(type='SQL')=13 AND SUM(version IN ('1','2','3','4','5','6','7','8','9','10','11','12','13'))=13 AND (SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1)='13','PASS','STOP') FROM flyway_schema_history;" \
  2> "$EVIDENCE/db-errors.log")
test "$gate" = PASS || { echo 'STOP schema history'; exit 1; }
echo 'PASS V1-V13 history gate; checksum/schema review still required'
```

성공한 versioned SQL 13개를 엄격하게 요구한다. baseline/repeatable/추가 이력이 있으면 자동 승인하지 않고 DBA가 조사한다. 최대 version만으로 판정하지 않는다. 후보 release SQL로 기존 체크리스트 §3의 Flyway `info`/`validate`를 읽기 전용 계정·callback 없는 환경에서 수행해 checksum 일치와 pending 0을 확인한다. DB 담당자가 실제 schema와 이력의 일치도 확인한다. `migrate/repair/baseline/clean/undo`는 실행하지 않는다.

## 3. Backend/systemd·frontend/nginx 최종 템플릿

- [systemd](staging/parkeasy-staging.service.example): `EnvironmentFile=/etc/parkeasy/staging.env`, 전용 non-root user `parkeasy`, WorkingDirectory, journal 로그, `Restart=no`. 리허설에서는 실패 반복을 막고 담당자 판단으로 재시작한다. ENV 파일은 root 0600, journal 접근은 운영자로 제한한다.
- [nginx](staging/nginx.conf.example): http context에 include한다. 80→443, TLS, frontend root, SPA fallback, `/api` 보존 proxy, forwarded headers, timeout, admin/legacy exact block, health 허용을 포함한다. domain/TLS 경로는 미확정 예시다.
- backend는 `/srv/parkeasy/backend-current/backend.jar`, frontend는 `/srv/parkeasy/frontend-current/dist`다. 최종적으로 같은 release ID를 가리키되 순서대로 전환한다. 작업 중 공개 트래픽을 차단하고 작업자만 검증한다.
- access log는 `$uri`를 쓰며 query가 포함된 `$request_uri`/`$request`, header/body를 기록하지 않는다. 이 리허설 vhost 예시는 nginx error log를 비활성화한다. LB/CDN/플랫폼 로그도 점검한다. backend 로그의 secret 비노출은 별도 scanner로 검증한다.
- nginx 단일 upstream은 ready 실패 시 자동 제외하지 않는다. 공개 전에 운영자 차단 절차 또는 health-aware LB를 확정한다.

ENV 주입 후 foreground 실행 명령(systemd와 동시에 실행하지 않는다):

```bash
java -jar /srv/parkeasy/backend-current/backend.jar \
  --spring.profiles.active=prod --server.address=127.0.0.1 --server.port=8080 \
  --server.forward-headers-strategy=framework \
  --spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=none \
  --spring.sql.init.mode=never --parkeasy.admin.enabled=false \
  --parkeasy.sync.facility.enabled=false \
  --parkeasy.sync.operation.enabled=false --parkeasy.sync.realtime.enabled=false
```

`staging` profile 파일이 없으므로 `prod` 단일 profile을 사용한다. DB 값은 ENV로만 전달한다. shell export는 systemd에 전달되지 않는다. ENV 파일을 shell `source`로 읽지 않는다. 서버에서 Java 경로·Java 17·OS user·디렉터리 권한을 확인한다.

## 4. 승인 후 실행 순서 A~L

release ID, 이전 backend/frontend release ID, 이전 nginx config 보관 위치를 기록한다. 아래 복사 원본 `/var/tmp/parkeasy-upload`는 승인 후 JAR·dist·migration·SHA256SUMS만 전송할 임시 경로 예시다. 로컬 디렉터리 전체, `.env*`, 소스, dump, 로그는 전송하지 않는다. 승인된 CI artifact에서 제공한 SHA256SUMS로 복사한 파일을 검증한다.

| 순서 | 실행 내용 / 통과 조건 |
|---|---|
| A | 위 SELECT로 V13 확인, checksum/schema 검증. 실패 시 STOP |
| B | backup ID/시각/무결성/암호화 보관/restore 증적 확인. 없으면 STOP |
| C | secret manager에서 systemd ENV 파일 주입. 값 출력 없이 필수 이름·profile 검증 |
| D | 후보 backend JAR/migration 배치·hash 확인. backend-current만 전환 |
| E | unit 등록/daemon-reload, backend start. 기존 서비스는 승인된 restart |
| F | loopback live=200/UP |
| G | loopback ready=200/UP. 실패하면 H 이후 중단 |
| H | dist 배치·hash 확인, frontend-current를 같은 release로 전환 |
| I | nginx 설정 백업 후 템플릿 설치. `nginx -t` 성공 후에만 reload |
| J | HTTPS health/API smoke 전체 PASS |
| K | UI smoke 전체 PASS |
| L | security smoke 전체 PASS, 담당자 sign-off 후 공개 여부 결정 |

아래는 D~I 작업 예시이며 **명시적 승인 전에는 실행 금지**다. 배포 계정에 필요한 권한만 부여하며 무제한 sudo를 전제로 하지 않는다.

```bash
set +x
set -euo pipefail
[[ "${RELEASE_ID:-}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || exit 1
RELEASE="/srv/parkeasy/releases/$RELEASE_ID"
# D: 신규 release만 생성. 기존 release를 덮어쓰지 않는다.
test ! -e "$RELEASE"
mkdir "$RELEASE"
install -m 0644 /var/tmp/parkeasy-upload/backend.jar "$RELEASE/backend.jar"
cp -R /var/tmp/parkeasy-upload/migration "$RELEASE/migration"
# CI backend/migration checksum 검증 후 전환.
(cd "$RELEASE" && sha256sum --check /var/tmp/parkeasy-upload/BACKEND-SHA256SUMS)
if test -e /srv/parkeasy/backend.next || test -L /srv/parkeasy/backend.next; then exit 1; fi
ln -s "$RELEASE" /srv/parkeasy/backend.next
mv -Tf /srv/parkeasy/backend.next /srv/parkeasy/backend-current
# E: 승인된 unit이 지정 위치에 설치되어 있어야 한다.
sudo systemctl daemon-reload
sudo systemctl restart parkeasy-staging
sudo systemctl is-active --quiet parkeasy-staging
# F / G: 200이 아니면 STOP. JSON도 UP이어야 한다.
for probe in live ready; do
  code=$(curl -sS --connect-timeout 3 --max-time 20 -o "$EVIDENCE/$probe.json" \
    -w '%{http_code}' "http://127.0.0.1:8080/api/health/$probe")
  test "$code" = 200
  jq -e '. == {"status":"UP"}' "$EVIDENCE/$probe.json" >/dev/null
done
# H: frontend는 ready 성공 후에만 전환.
cp -R /var/tmp/parkeasy-upload/dist "$RELEASE/dist"
test -f "$RELEASE/dist/index.html"
(cd "$RELEASE" && sha256sum --check /var/tmp/parkeasy-upload/SHA256SUMS)
if test -e /srv/parkeasy/frontend.next || test -L /srv/parkeasy/frontend.next; then exit 1; fi
ln -s "$RELEASE" /srv/parkeasy/frontend.next
mv -Tf /srv/parkeasy/frontend.next /srv/parkeasy/frontend-current
# I: 실제 domain/TLS 설정 완료, 이전 config 보관 완료 상태.
sudo nginx -t
sudo systemctl reload nginx
```

D의 hash 검사는 backend/migration만 포함하는 CI manifest를 사용한다. A의 Flyway 검사용 SQL은 승인된 후보 artifact에서 읽을 수 있도록 사전 준비하며 DB에 적용하지 않는다. 업로드 방법/대상/시각은 승인 시 확정한다. SSH host key 검증을 끄지 않는다.

## 5. Smoke 최종 실행 세트

[go-live checklist §6~9](staging-go-live-checklist.md)의 Bash 블록을 순서대로 실행한다. `BASE`는 실제 staging HTTPS origin, `EVIDENCE`는 접근 제한된 증적 경로다. status/JSON/limit/정렬/detail 일치/CORS header 검증도 생략하지 않는다. 아래는 요청 대상 확인용 curl 목록이며 HTTP 200만으로 PASS 판정하지 않는다.

```bash
BASE=https://staging.parkeasy.invalid
QUERY='latitude=37.5547&longitude=126.9706&radius=2000&limit=100'
curl --fail -sS --max-time 20 "$BASE/api/health/live"
curl --fail -sS --max-time 20 "$BASE/api/health/ready"
for sort in DISTANCE FEE AVAILABLE; do
  curl --fail -sS --max-time 20 "$BASE/api/parking-lots/nearby?$QUERY&sort=$sort" \
    -o "$EVIDENCE/$sort.json"
done
ID=$(jq -er '.[0].id | select(type == "number" and . > 0)' "$EVIDENCE/DISTANCE.json")
curl --fail -sS --max-time 20 "$BASE/api/parking-lots/$ID" -o "$EVIDENCE/detail.json"
# ALLOWED_ORIGIN은 BASE와 다른, 허용 목록에 등록한 시험 origin.
curl -sS --max-time 20 -D "$EVIDENCE/cors.headers" -o "$EVIDENCE/cors.body" \
  -H "Origin: $ALLOWED_ORIGIN" -H 'Access-Control-Request-Method: GET' \
  -X OPTIONS "$BASE/api/parking-lots/nearby?$QUERY"
for path in /api/admin /api/admin/parking-sync/facilities/full/status \
  /api/parking-lots /api/parking-lots/ '/api/parking-lots?limit=100'; do
  code=$(curl -sS --max-time 20 -o /dev/null -w '%{http_code}' "$BASE$path")
  test "$code" = 404 || { echo 'STOP edge policy'; exit 1; }
done
```

기대값: health/API/detail 200, admin/legacy 404, 허용 cross-origin GET/OPTIONS 200과 정확한 ACAO, 비허용 GET/OPTIONS 403과 ACAO 없음. 전체 CORS 검증은 기존 §9의 allowed/denied 루프를 모두 실행한다. TLS 실패를 `curl -k`로 우회하지 않는다. 입력 오류 400·stacktrace/SQL 비노출, 기동~smoke 로그의 secret scanner 검사도 필수다.

UI 체크리스트(브라우저, DB write 없음):

- [ ] 서울역 검색·선택, 목록과 지도 일치
- [ ] 최대 100건 표시. 반경 내 후보가 100건 미만이면 100건을 요구하지 않는다. 100건 부하 검증은 기존 데이터가 부족하면 NOT RUN
- [ ] 줌에 따른 marker cluster 집계/펼침과 개별 marker 선택
- [ ] DISTANCE/FEE/AVAILABLE 전환, detail 요금·시간 일치
- [ ] stale은 수치와 업데이트 지연 표시, 0은 잔여 0면 표시(만차 확정 아님), 결측은 정보 없음
- [ ] unsupported filter 비활성화와 안내
- [ ] 모바일 폭 390px에서 가로 overflow 없음, detail 조작 가능
- [ ] 새로고침/SPA fallback, API 오류 표시, Kakao domain 제한, console 민감정보 비노출

데이터 부족은 NOT RUN이다. seed/sync로 채우지 않는다. DB 장애 readiness는 별도 인스턴스의 DB proxy만 차단하는 기존 §10의 향후 절차를 따른다. 이번에는 실행하지 않는다. 기대값은 live=200/ready=503, 복구 후 모두 200이다.

## 6. Rollback 리허설 / STOP gate

실제 rollback은 미실행이다. 이전 backend JAR/dist/hash와 nginx config 백업 존재를 담당자가 확인한다. 최초 배포라 이전 release가 없으면 서비스 중지·공개 철회가 rollback이다.

1. 트래픽 차단, 신규 backend 중지. 이전 버전의 schema 호환성 확인.
2. `backend-current`를 이전 backend release로, `frontend-current`를 이전 frontend release로 임시 symlink+atomic rename으로 복원한다. 이전 ID가 같다고 가정하지 않고 기록을 사용한다.
3. nginx 변경 시 보관 config 복원, `nginx -t` 성공 후 reload.
4. 이전 backend 기동, live/ready/API/UI/security 재검증. PASS 후에만 트래픽 복구.
5. 이번에는 DB write/migration이 없으므로 앱 오류만으로 DB restore하지 않는다. 향후 비호환 migration·부분 DDL·데이터 손상 시 DBA가 결정한다. 복구 대상은 격리 DB이며 backup/restore 명령과 RPO/RTO 확인은 기존 §11을 따른다. restore는 DB 쓰기이므로 이번에 실행 금지다.

**하나라도 해당하면 STOP**: DB version≠13/이력 누락/실패/checksum 불일치, backup 없음, ENV/대상 미확정, ready≠200, admin 노출, legacy 노출, CORS 이상, smoke FAIL 또는 필수 항목 NOT RUN, secret 로그 노출 또는 검사 미실시. 미해결 상태로 다음 단계나 공개를 진행하지 않는다.

## 7. 로컬 검증 결과 / 승인 범위

- PASS: boot JAR와 dist/index.html 존재. JAR 내 V1~V13 SQL 13개 모두 소스와 SHA-256 일치.
- PASS: Markdown 코드 블록 균형, systemd 안전 옵션, nginx routing 옵션, HTTP/HTTPS 양쪽의 query 없는 access log 형식 정적 검사. 실제 `nginx -t` 결과를 뜻하지 않는다.
- PASS: 소스 prod 설정·health/controller/CORS/정렬과 기존 smoke의 일치 확인.
- NOT RUN: 실제 DB V13, backup, SSH, HTTP, 브라우저, systemd, nginx 실기 문법 검사. 로컬 PATH에 nginx/Bash/jq 없음. mysql/SSH CLI는 있으나 대상이 미확정이므로 접속하지 않았다.
- artifact 존재/SQL 일치는 JAR 전체의 최신성이나 CI/commit 추적성을 보장하지 않는다.

실제 변경 전 필요한 명시적 승인: (1) 확정 host의 SSH 접속, (2) 승인 release 파일 전송과 symlink 전환, (3) ENV/unit/config 설치와 daemon-reload, (4) backend start/restart, (5) nginx reload, (6) 실제 rollback 필요 시 대상과 실행. DB migration/sync/scheduler 활성화/production은 이번 승인 범위에 포함하지 않는다.

준비할 것: staging host/IP·SSH user/port/키 참조, 실제 domain/TLS 방식, deploy/config/service 경로, DB endpoint와 SELECT용 secret 참조, V13/backup/restore 증적, 승인 artifact/이전 release, Kakao domain/CORS origin, 위 staging 작업의 명시적 승인.

참조: access log 형식은 [nginx log module 공식 문서](https://nginx.org/en/docs/http/ngx_http_log_module.html)로 확인했다. [systemd.exec 공식 문서](https://www.freedesktop.org/software/systemd/man/latest/systemd.exec.html)는 이번 조회에서 403으로 열리지 않았다. ENV 파일·실행 권한은 서버의 `man systemd.exec` 및 실제 설치 버전으로 재확인하고, 대상 서버의 nginx include 설정도 확인해야 한다.
