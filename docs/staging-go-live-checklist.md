# ParkEasy staging go-live checklist

> 실제 실행 준비의 최신 순서·인프라 조사 결과·분리된 backend/frontend 전환 템플릿은 [staging 실행 리허설](staging-execution-rehearsal.md)을 따른다. 아래 단일 `current` 전환 예시는 새 템플릿과 혼용하지 않는다. 검증과 PASS/FAIL 기준은 유지한다.

작성 기준: 2026-09-27. 이 문서는 **실행 계획**이다. 실제 서버/DB 검증 결과는 아직 없다.
기존 [deployment runbook](staging-deployment-runbook.md)을 보완하며, 이번 리허설에서는 이 문서의 더 제한적인 절차를 따른다.
production 배포, 실제 migration, scheduler 활성화, full sync, DB write 테스트, secret 출력은 금지한다.
아래 서버 명령은 향후 staging 작업용이며 이번 문서 작성 중 실행하지 않았다. 복구 명령 역시 별도 복구 작업 때만 실행한다.

## 1. Pre-deploy / 실행 전제

- [ ] 대상이 production과 분리된 staging 호스트/DB임을 담당자가 확인했다.
- [ ] Linux + Bash, Java 17, nginx, curl, jq, MySQL client가 준비되어 있다. 로컬 PowerShell에서 아래 Bash 명령을 그대로 실행하지 않는다.
- [ ] release ID/소스 commit/CI 결과/JAR·dist·migration SHA-256을 기록했다. boot JAR만 사용하며 `*-plain.jar`는 제외한다.
- [ ] `/srv/parkeasy/releases/<release-id>/backend.jar`, `dist/`, `migration/`, 체크섬 manifest를 같은 release에서 가져왔다. `migration/`은 JAR 내 SQL과 바이트 단위로 일치한다.
- [ ] `/srv/parkeasy/current`는 활성 release symlink이고 이전 release는 보존되어 있다. 최초 배포라 이전 release가 없으면 rollback은 서비스 철회로 기록한다.
- [ ] nginx가 dist를 읽을 수 있고 앱 전용 OS 계정은 secret 파일을 읽을 수 있다. `.env*`, dump, 로그, 소스는 web root 밖에 둔다.
- [ ] staging DNS/TLS 인증서/방화벽 준비 완료. 외부에는 80/443만 공개하고 backend 8080은 loopback에만 바인딩한다.
- [ ] V13까지 적용된 기존 staging schema와 서울역 주변 승인된 데이터가 있다. 없으면 **중단**한다. 이번 단계에서 migration/seed/sync로 채우지 않는다.
- [ ] backup ID/시각/암호화 저장 위치/무결성/복구 담당자/복구 소요시간/RPO·RTO를 기록했다. backup 존재만으로 restore 성공을 간주하지 않는다.

현재 소스에는 `application-staging.properties`가 없다. **staging에서도 `prod` 단일 profile**을 사용한다. `local`, `test`, `staging` 단독 또는 혼합 profile을 사용하지 않는다. prod의 admin controller에는 `@Profile("!prod")`가 있어 등록되지 않는다.

## 2. Staging ENV checklist — 이름만 기록

실제 값은 secret manager/서비스 관리자가 주입한다. 이 표와 sign-off에는 값을 붙여 넣지 않는다.

| 용도 | 변수명 | 확인 |
|---|---|---|
| DB URL | `PARKEASY_DB_URL` | 필수, staging DB·읽기 전용 계정과 연결 |
| DB username | `PARKEASY_DB_USERNAME` | 필수 |
| DB password | `PARKEASY_DB_PASSWORD` | 필수 |
| serviceKey | `PARKEASY_PUBLIC_DATA_SERVICE_KEY` | read-only web에서는 불필요, 주입하지 않아도 됨 |
| CORS origin | `PARKEASY_CORS_ALLOWED_ORIGINS` | 이번 허용/비허용 origin smoke를 위해 명시 |
| Kakao JavaScript key | `VITE_KAKAO_MAP_KEY` | frontend **build 시점** 필수 |
| profile | `SPRING_PROFILES_ACTIVE` | 필수, 아래 시작 명령에서도 고정 |

Vite 값은 이미 dist에 반영되므로 nginx 실행 시 ENV를 넣어도 바뀌지 않는다. Kakao 콘솔의 staging 웹 도메인 등록과 build 환경을 검증하고, 필요하면 CI에서 dist를 다시 만든다. Kakao JS key는 브라우저 공개용이며 DB password/serviceKey를 `VITE_*`에 넣지 않는다.

값을 출력하지 않는 Bash 검사(서버 secret 주입 후):

```bash
set +x
set -euo pipefail
for name in SPRING_PROFILES_ACTIVE PARKEASY_DB_URL PARKEASY_DB_USERNAME PARKEASY_DB_PASSWORD PARKEASY_CORS_ALLOWED_ORIGINS; do
  test -n "${!name:-}" || { printf 'FAIL missing: %s\n' "$name"; exit 1; }
done
test "$SPRING_PROFILES_ACTIVE" = prod || { echo 'FAIL profile'; exit 1; }
```

`env`, `printenv`, `set -x`, JVM 환경 덤프, secret 포함 CLI 옵션을 사용하지 않는다. 서비스 ENV 파일은 web root 밖에서 소유자만 읽게 관리하고 내용을 출력하지 않는다.

## 3. Flyway dry-run — 조회와 정적 검토만

여기서 dry-run은 **적용 계획 확인**이며 DDL 실행 시뮬레이션 성공을 뜻하지 않는다. Flyway CLI는 JAR에 포함된 버전과 호환되는 고정 버전을 사용한다. Spring boot JAR를 Flyway 실행기로 사용하지 않는다.

1. 읽기 전용 MySQL client 설정 파일(`/run/secrets/parkeasy-mysql-readonly.cnf`, 권한 0600, staging database 포함)을 운영자가 준비한다. 파일/접속 오류의 원문은 공유하지 않는다.
2. 다음 SELECT만 실행한다. history가 없으면 첫 쿼리 결과 0으로 판정하고 두 번째 쿼리를 실행하지 않는다. 기본 history table 이름을 변경했다면 실제 이름을 먼저 확인한다.

```bash
umask 077
mysql --defaults-extra-file=/run/secrets/parkeasy-mysql-readonly.cnf --batch \
  -e "SELECT COUNT(*) AS history_exists FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='flyway_schema_history';"
# history_exists=1인 경우에만:
mysql --defaults-extra-file=/run/secrets/parkeasy-mysql-readonly.cnf --batch \
  -e "SELECT installed_rank,version,description,type,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank;"
```

3. 승인된 release의 `migration/`만 대상으로 SQL/checksum을 검토한다. 외부 callbacks, 임의 config/init SQL, 자동 migrate wrapper가 없는 깨끗한 Flyway 실행 환경을 사용한다. 읽기 전용 계정으로 `FLYWAY_URL`, `FLYWAY_USER`, `FLYWAY_PASSWORD`를 별도 주입한다. 앱 ENV가 자동으로 Flyway CLI에 연결되는 것은 아니다.

```bash
# stdout/stderr에 접속 URL이 나올 수 있어 보호 파일에만 저장하고 공유하지 않는다.
umask 077
mkdir -p "$HOME/parkeasy-private-evidence"
: "${RELEASE_ID:?Set approved candidate release ID}"
[[ "$RELEASE_ID" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || exit 1
CANDIDATE="/srv/parkeasy/releases/$RELEASE_ID"
test -d "$CANDIDATE/migration"
flyway -locations="filesystem:$CANDIDATE/migration" -cleanDisabled=true info \
  > "$HOME/parkeasy-private-evidence/flyway-info.log" 2>&1
flyway -locations="filesystem:$CANDIDATE/migration" -cleanDisabled=true validate \
  > "$HOME/parkeasy-private-evidence/flyway-validate.log" 2>&1
```

현재 배포 대상은 V13이어야 한다. pending이 있는 초기 계획 검토에서 validate가 실패하면 원인을 분류하되 이를 무시하고 배포하지 않는다. checksum 불일치, Failed/Missing/Future migration, history와 실제 schema 불일치, 예상하지 않은 baseline은 중단 사유다. `migrate`, `clean`, `repair`, `baseline`, `undo`는 실행하지 않는다. `migrate -dryRunOutput`도 이번 범위에서는 사용하지 않는다. `info`/`validate`의 역할과 dry-run 기능 구분은 [Flyway 공식 명령 문서](https://documentation.red-gate.com/flyway/reference/commands)를 참고한다.

| 순서 | 예상 변경 |
|---|---|
| V1 | parking_lot 생성 |
| V2 | sample parking_lots INSERT — 실제 데이터 쓰기가 포함됨 |
| V3 | parking_operation 생성 |
| V4 | parking_realtime 생성 |
| V5 | parking_sync_job 생성 |
| V6 | sync worker 상태 추가 |
| V7 | 실패 분류 추가 |
| V8 | parking_sync_skipped_page 생성 |
| V9 | empty observation 추가 |
| V10 | trailing empty 상태 추가 |
| V11 | operation full sync counts 추가 |
| V12 | realtime full sync counts 추가 |
| V13 | realtime match diagnostics 추가 |

빈 DB에는 V1~V13 전부가 예상되며 현재 단계에서는 배포 중단이다. Vn까지 정상 적용되어 있으면 n+1~13이 예상되며 역시 별도 migration 작업이 필요하다. V13이면 pending 0, 모든 version 성공, checksum 검증 통과가 필요하다. history의 최대 숫자 하나만 보고 완료로 판단하지 않는다. 실제 현재 DB version은 **미확인**이다.

향후 별도 승인된 migration에서 SQL 오류, lock timeout, checksum/schema 불일치, backup 불충분, 예상치 않은 데이터 변경이 발생하면 즉시 중단하고 트래픽을 열지 않는다. MySQL DDL의 부분 적용 가능성을 고려해 자동 재시도/repair로 덮지 말고 DBA가 복구 방식을 결정한다.

## 4. Backend deploy / 시작 명령

Flyway 검사는 `current`가 아니라 **신규 후보 release**의 SQL로 수행한다. 최초 배포에는 current가 없을 수 있고, 기존 current는 이전 버전일 수 있다.

서비스 이름은 `parkeasy-staging`, 실행 사용자는 전용 계정으로 구성한다. 서비스 관리자의 ExecStart 또는 foreground 진단에서 아래 명령을 사용한다. 서비스 관리자가 위 ENV를 주입하도록 하고 셸 수동 실행만으로 상시 운영하지 않는다.

```bash
java -jar /srv/parkeasy/current/backend.jar \
  --spring.profiles.active=prod \
  --server.address=127.0.0.1 --server.port=8080 \
  --server.forward-headers-strategy=framework \
  --spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=none \
  --spring.sql.init.mode=never \
  --parkeasy.admin.enabled=false \
  --parkeasy.sync.facility.enabled=false \
  --parkeasy.sync.operation.enabled=false \
  --parkeasy.sync.realtime.enabled=false
```

- [ ] ENV/서비스 실행 명령의 안전 설정을 검토했고 다른 JVM/config override가 없다.
- [ ] web DB 계정은 SELECT만 허용하며 schema 변경/수집 쓰기 권한이 없다.
- [ ] release 파일 체크섬을 검증했다. 이전 release ID를 기록했다.
- [ ] 신규 release로 current를 바꾼 뒤 서비스를 시작한다. public ingress는 검증 완료까지 접근 제한 상태로 둔다.

Linux systemd 예시(`/etc/systemd/system/parkeasy-staging.service`). 전용 `parkeasy` 계정과 디렉터리는 서버 관리자가 준비하고, ENV 파일은 root 소유 0600으로 secret manager가 생성한다. shell의 export는 systemd에 자동 전달되지 않는다. 아래 파일 등록은 향후 staging에서만 수행한다.

```ini
[Unit]
Description=ParkEasy staging read-only web
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=parkeasy
Group=parkeasy
WorkingDirectory=/srv/parkeasy/current
EnvironmentFile=/etc/parkeasy/staging.env
ExecStart=/usr/bin/java -jar /srv/parkeasy/current/backend.jar --spring.profiles.active=prod --server.address=127.0.0.1 --server.port=8080 --server.forward-headers-strategy=framework --spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=none --spring.sql.init.mode=never --parkeasy.admin.enabled=false --parkeasy.sync.facility.enabled=false --parkeasy.sync.operation.enabled=false --parkeasy.sync.realtime.enabled=false
Restart=no
UMask=0077
NoNewPrivileges=true
PrivateTmp=true
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

`/usr/bin/java`가 Java 17인지 서버에서 확인한다. journal 접근은 운영자로 제한하며 로그 원문은 공유하지 않는다. 리허설에서는 실패를 숨기는 자동 재시작 없이 중단 원인을 확인한다. unit 설치/변경 후 `sudo systemctl daemon-reload`를 수행하고 아래 release 전환을 진행한다. `is-active`는 readiness 통과를 의미하지 않는다.

```bash
# RELEASE_ID는 사전에 검증된 staging release 디렉터리 이름. secret이 아니다.
[[ "${RELEASE_ID:-}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || exit 1
test ! -e /srv/parkeasy/current.next && test ! -L /srv/parkeasy/current.next
test -f "/srv/parkeasy/releases/$RELEASE_ID/backend.jar"
test -f "/srv/parkeasy/releases/$RELEASE_ID/dist/index.html"
ln -s "/srv/parkeasy/releases/$RELEASE_ID" /srv/parkeasy/current.next
mv -Tf /srv/parkeasy/current.next /srv/parkeasy/current
sudo systemctl restart parkeasy-staging
sudo systemctl is-active --quiet parkeasy-staging
```

current.next가 이미 있으면 중단하고 담당자가 확인한다. backend와 frontend를 같은 release로 전환하며 검증 중 새/구 asset 혼합 요청을 방지하도록 접근을 제한한다.

## 5. Frontend / nginx 예시

`HTTPS :443 → dist` 및 `/api/* → 127.0.0.1:8080`의 단일 origin 구조다. frontend 소스는 상대 경로 `/api`를 호출한다. 아래 예시는 nginx `http` context에 include한다. `.invalid` 호스트와 인증서 경로는 배포 환경에 맞게 치환한다. 현재 파일은 실제 설치된 nginx 설정이 아니다.

```nginx
# http context; query/header/body를 access log에 기록하지 않는다.
log_format parkeasy_safe '$remote_addr $request_method $uri $status $request_time';

server {
    listen 80;
    server_name staging.parkeasy.invalid;
    access_log /var/log/nginx/parkeasy-staging-access.log parkeasy_safe;
    error_log /dev/null crit;
    return 308 https://staging.parkeasy.invalid$request_uri;
}

server {
    listen 443 ssl;
    server_name staging.parkeasy.invalid;
    ssl_certificate /etc/nginx/tls/parkeasy-staging/fullchain.pem;
    ssl_certificate_key /etc/nginx/tls/parkeasy-staging/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    server_tokens off;
    root /srv/parkeasy/current/dist;
    index index.html;
    client_max_body_size 1m;
    access_log /var/log/nginx/parkeasy-staging-access.log parkeasy_safe;
    # nginx error log는 오류 시 request/query를 포함할 수 있다. 이 read-only
    # 리허설 vhost에서는 비활성화하고 status/latency와 안전한 앱 로그로 관측한다.
    error_log /dev/null crit;

    location = /api/admin { return 404; }
    location ^~ /api/admin/ { return 404; }
    # exact match: query string 유무와 무관하게 전체목록만 차단한다.
    location = /api/parking-lots { return 404; }
    location = /api/parking-lots/ { return 404; }
    location = /api { return 404; }

    location /api/ {
        # URI suffix 없이 /api 경로를 그대로 보존한다.
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Port $server_port;
        proxy_set_header X-Forwarded-Proto $scheme;
        # 단일 edge 구조: 외부에서 위조한 forwarded 체인은 폐기한다.
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header Forwarded "";
        proxy_set_header Connection "";
        proxy_connect_timeout 3s;
        proxy_send_timeout 15s;
        proxy_read_timeout 15s;
        proxy_intercept_errors off;
    }
    location ~ /\. { return 404; }
    location /assets/ { try_files $uri =404; }
    location = /index.html { add_header Cache-Control "no-store"; }
    location / { try_files $uri $uri/ /index.html; }
}
```

health/live·ready, nearby, detail은 `/api/`에서 proxy한다. API 오류가 SPA HTML로 바뀌면 FAIL이다. legacy prefix 전체를 차단하면 nearby/detail도 막히므로 금지한다. 다른 include의 정규식 location/LB/WAF rewrite와 충돌하지 않는지 함께 검토한다. LB가 앞에 추가되면 신뢰할 proxy CIDR과 real IP 처리를 별도로 확정한다. URI 없는 proxy_pass 동작은 [nginx 공식 문서](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass)를 따른다.

```bash
sudo nginx -t
# staging에 설정을 설치한 후, 문법 검사 성공 시에만:
sudo systemctl reload nginx
```

`nginx -T` 전체 출력은 공유하지 않는다. TLS 검증에 `curl -k`를 쓰지 않는다.

## 6. Health 검증

아래 BASE는 실제 staging HTTPS origin으로 설정한다. 증적은 접근 제한된 디렉터리에 저장한다. 명령의 네트워크 오류/비정상 HTTP는 즉시 FAIL이다.

```bash
set -euo pipefail
set +x
umask 077
BASE=https://staging.parkeasy.invalid
EVIDENCE="$HOME/parkeasy-private-evidence"
mkdir -p "$EVIDENCE"
for path in live ready; do
  code=$(curl -sS --connect-timeout 3 --max-time 20 \
    -o "$EVIDENCE/health-$path.json" -w '%{http_code}' "$BASE/api/health/$path")
  test "$code" = 200
  jq -e '. == {"status":"UP"}' "$EVIDENCE/health-$path.json" >/dev/null
done
# 서버 내부에서도 edge와 분리해 확인:
curl --fail -sS --max-time 10 http://127.0.0.1:8080/api/health/live
curl --fail -sS --max-time 10 http://127.0.0.1:8080/api/health/ready
```

두 endpoint 모두 200/UP가 정상이다. live는 프로세스 응답, ready는 읽기 전용 SELECT 1 및 rollback만 확인한다. schema V13/업무 데이터/외부 공급자 freshness는 보장하지 않는다. ready 503/DOWN, 502/504, TLS 오류, timeout이면 트래픽을 열지 않는다.

## 7. API smoke — 서울역 (37.5547, 126.9706)

모두 GET이다. 서울역 반경 2km에 기존 데이터가 있어야 하며 빈 배열은 API 성공일 수 있어도 이번 detail/정렬 검증 완료로 인정하지 않는다.

```bash
QUERY='latitude=37.5547&longitude=126.9706&radius=2000'
get200() {
  local code
  code=$(curl -sS --connect-timeout 3 --max-time 20 -o "$EVIDENCE/$1.json" \
    -w '%{http_code}' "$BASE$2")
  test "$code" = 200 || { echo "FAIL $1 HTTP $code"; return 1; }
}
get200 nearby "/api/parking-lots/nearby?$QUERY"
get200 limit100 "/api/parking-lots/nearby?$QUERY&limit=100"
for sort in DISTANCE FEE AVAILABLE; do
  get200 "$sort" "/api/parking-lots/nearby?$QUERY&limit=100&sort=$sort"
done
for name in nearby limit100 DISTANCE FEE AVAILABLE; do
  jq -e 'type == "array" and length > 0 and length <= 100 and
    all(.[]; (.id | type == "number") and .distanceMeters >= 0 and .distanceMeters <= 2000)' \
    "$EVIDENCE/$name.json" >/dev/null
done
jq -e '. == sort_by(.distanceMeters)' "$EVIDENCE/DISTANCE.json" >/dev/null
jq -e '. == sort_by([(.baseFee == null), (.baseFee // 0), .distanceMeters])' \
  "$EVIDENCE/FEE.json" >/dev/null
jq -e '. == sort_by([(if .availableSpaces == null then 2 elif .stale == false then 0 else 1 end),
  (-(.availableSpaces // 0)), .distanceMeters])' "$EVIDENCE/AVAILABLE.json" >/dev/null
ID=$(jq -er '.[0].id | select(type == "number" and . > 0)' "$EVIDENCE/DISTANCE.json")
get200 detail "/api/parking-lots/$ID"
jq -e --argjson id "$ID" '.id == $id' "$EVIDENCE/detail.json" >/dev/null
jq -e --slurpfile rows "$EVIDENCE/DISTANCE.json" \
  '.baseFee == $rows[0][0].baseFee and .baseTime == $rows[0][0].baseTime' \
  "$EVIDENCE/detail.json" >/dev/null
```

PASS: 각 200/정상 JSON, limit 이하, DISTANCE 거리 오름차순, FEE 알려진 baseFee 오름차순·null 마지막·동률 거리순. AVAILABLE은 fresh → stale/불명 → 수치 없음, 그룹 내 잔여면 내림차순·동률 거리순이다. 세 정렬의 상위 100 ID 집합은 달라도 정상이다. 정렬 기준을 구분할 다양한 데이터가 없으면 해당 검증은 NOT RUN이며 전체 PASS가 아니다.

## 8. UI smoke

- [ ] HTTPS 첫 로딩과 새로고침 정상. index.html 및 hashed asset이 현재 release와 일치한다.
- [ ] 임의 SPA 경로 직접 진입/새로고침에서 index.html 반환, API 경로에서는 SPA fallback 없음.
- [ ] 지도와 Kakao 장소검색 정상, 서울역 선택 후 nearby 1회 요청, map/list 일치.
- [ ] DISTANCE/FEE/AVAILABLE 선택과 detail 정상, card/detail baseFee/baseTime 일치.
- [ ] 잔여 0면은 만차 확정으로 표현하지 않음. stale은 숫자와 업데이트 지연 표시, 수치 없음은 실시간 정보 없음 표시.
- [ ] 미지원 공영/민영/무료/24시간 분류 controls 비활성 및 설명 확인.
- [ ] 390×844에서 가로 overflow 없음, 상세 패널 화면 안에 표시.
- [ ] DevTools의 해당 API 요청 차단으로 error/loading 상태 검증. 실제 DB/서버를 중단하지 않는다. 차단 해제 후 회복 확인.
- [ ] 브라우저 console 오류/민감정보 노출 없음. key 포함 SDK URL/네트워크 HAR 원문은 공유하지 않는다.

## 9. Security smoke

```bash
# edge에서 정확히 404. query를 붙여도 legacy block 유지.
for path in /api/admin /api/admin/ /api/admin/parking-sync/facilities/full/status \
  /api/parking-lots /api/parking-lots/ '/api/parking-lots?limit=1'; do
  code=$(curl -sS --max-time 20 -o /dev/null -w '%{http_code}' "$BASE$path")
  test "$code" = 404 || { echo 'FAIL edge policy'; exit 1; }
done
# backend 미등록 확인: GET만 사용하며 POST/sync는 호출하지 않는다.
code=$(curl -sS --max-time 10 -o /dev/null -w '%{http_code}' \
  http://127.0.0.1:8080/api/admin/parking-sync/facilities/full/status)
test "$code" = 404

# ALLOWED_ORIGIN은 BASE와 다른, 허용 목록에 등록한 테스트 origin이다.
# 같은 origin이면 브라우저 CORS 처리 대상이 아니므로 ACAO 검증을 대체하지 못한다.
: "${ALLOWED_ORIGIN:?Set one configured staging origin}"
test "$ALLOWED_ORIGIN" != "$BASE" || { echo 'FAIL use a cross-origin test origin'; exit 1; }
header_value() {
  awk -v key="$2" 'tolower($1) == tolower(key) ":" {
    sub(/^[^:]*:[ \t]*/, ""); sub(/\r$/, ""); print
  }' "$1"
}
for kind in allowed denied; do
  origin="$ALLOWED_ORIGIN"
  if test "$kind" = denied; then origin=https://untrusted.invalid; fi
  for method in GET OPTIONS; do
    extra=()
    if test "$method" = OPTIONS; then extra=(-H 'Access-Control-Request-Method: GET'); fi
    code=$(curl -sS --max-time 20 -X "$method" -H "Origin: $origin" "${extra[@]}" \
      -D "$EVIDENCE/cors-$kind-$method.headers" \
      -o "$EVIDENCE/cors-$kind-$method.body" \
      -w '%{http_code}' "$BASE/api/parking-lots/nearby?$QUERY&limit=100")
    headers="$EVIDENCE/cors-$kind-$method.headers"
    acao=$(header_value "$headers" Access-Control-Allow-Origin)
    if test "$kind" = allowed; then
      if test "$code" != 200 || test "$acao" != "$ALLOWED_ORIGIN"; then
        echo 'FAIL allowed CORS'; exit 1
      fi
      if test "$method" = OPTIONS; then
        methods=$(header_value "$headers" Access-Control-Allow-Methods)
        [[ ",$methods," =~ (^|,)[[:space:]]*GET[[:space:]]*(,|$) ]]
      fi
    else
      if test "$code" != 403 || test -n "$acao"; then
        echo 'FAIL denied CORS'; exit 1
      fi
    fi
  done
done
# 의도적인 입력 오류, DB write 없음
code=$(curl -sS --max-time 20 -o "$EVIDENCE/error400.json" -w '%{http_code}' \
  "$BASE/api/parking-lots/nearby?latitude=91&longitude=126.9706")
test "$code" = 400
jq -e 'type == "object" and (has("trace") | not) and (has("exception") | not)' \
  "$EVIDENCE/error400.json" >/dev/null
```

| 항목 | PASS | FAIL |
|---|---|---|
| admin | edge 404 + prod controller 미등록 검토 + 내부 GET 404 | 2xx/redirect/실행 가능 endpoint |
| legacy 전체목록 | 두 exact 경로 모두 query와 무관하게 404 | 전체목록 노출 또는 nearby/detail까지 차단 |
| 허용 CORS | GET/OPTIONS 200, ACAO가 요청 origin과 정확히 일치, OPTIONS의 Allow-Methods에 GET | wildcard/누락/다른 origin/5xx |
| 비허용 CORS | 현재 Spring 구성에서 GET/OPTIONS 403, ACAO 없음 | ACAO 허용 또는 2xx preflight |
| 에러 | 400 JSON, stacktrace/exception/SQL/설정값 없음 | 내부 정보 노출/HTML SPA/500 |
| 로그 | 보호 로그 검사에서 실제 secret 일치 0, stacktrace/credential URL 노출 0 | 1건 이상 또는 검사 미실시 |

단일 origin UI는 CORS 없이도 동작하지만 이번 허용 origin 검증을 위해 CORS ENV를 설정한다. CORS 대상은 parking-lots이며 health로 CORS를 시험하지 않는다. CORS는 curl 차단/인증 기능이 아니다.

로그 검사는 운영자의 보호 세션에서 수행한다. 수집 범위는 시작~smoke 종료의 앱 stdout/stderr, nginx access, 플랫폼/LB 로그다. secret manager가 제공한 민감값(DB password, serviceKey, 인증 포함 URL 등)을 메모리에서 대조하는 승인된 scanner를 사용하고 출력은 **파일 식별자·검사 건수·일치 건수·PASS/FAIL만** 남긴다. 매칭 줄/값/환경 덤프를 출력하는 grep, scanner debug는 금지한다. scanner 미준비는 NOT RUN이다. 에러 본문도 동일하게 검사한다. nginx 이외 플랫폼 access log가 query를 기록하지 않는지 확인한다.

## 10. DB 장애 readiness — 향후 staging 절차만

이번 단계에서 장애 주입/DB 종료/접속 설정 변경은 실행하지 않는다.

1. DB 정상 상태에서 **트래픽에 연결하지 않은 별도 staging 앱 인스턴스**를 시작하고 live/ready 200을 확인한다. prod profile·scheduler/admin/Flyway OFF·읽기 전용 DB 권한은 동일하게 유지한다.
2. 그 인스턴스만 사용하는 DB 네트워크 proxy를 미리 구성한다. 정상 연결을 확인한 뒤 proxy의 해당 listener에서 **기존 연결도 끊고** 새 연결을 거부하도록 한다. DB 서버와 다른 인스턴스 네트워크는 건드리지 않는다. idle pooled connection이 살아 있으면 장애 주입 성공이 아니다.
3. 별도 앱의 loopback 주소에서 `curl -sS --max-time 20 -o <보호파일> -w '%{http_code}' http://127.0.0.1:<별도포트>/api/health/live` 및 `/ready`를 반복 확인한다.
4. **live=200/UP, ready=503/DOWN**을 연속 3회 확인한다. 이번 리허설 기준 각 요청 20초 이내이며 HTTP 000/timeout/502/504는 503 성공으로 간주하지 않는다. fail-fast 연결 거부로 시험하고 silent DROP은 별도 timeout 검증 과제로 분리한다.
5. proxy 연결을 복원하고 ready=200 연속 3회 및 live=200 회복을 확인한다. proxy 장애 설정을 제거하고 별도 인스턴스를 종료한다.
6. readiness 실패 인스턴스가 트래픽 대상에서 제외되는지 LB/서비스 관리 계층을 검증한다. 위 단일 upstream nginx 예시는 자동 readiness routing을 구현하지 않으므로 운영자가 접근을 차단하거나 별도 health-aware LB가 필요하다.

처음부터 잘못된 DB URL로 실행하면 JPA 초기화 실패로 앱이 시작되지 않을 수 있어 live=200 검증을 대체할 수 없다. ready의 SELECT 1은 rollback되며 업무 DB write 테스트를 포함하지 않는다.

## 11. Rollback runbook

트리거: ready 실패, API/UI/security FAIL, 데이터/schema 이상, 승인된 검증 시간 초과. 신규 release에 트래픽을 열지 않거나 즉시 철회한다. scheduler/admin은 계속 OFF다.

**앱 rollback (향후 staging 실행용):** 이전 JAR와 dist의 release ID/checksum/schema 호환성을 먼저 확인한다. 아래 current는 symlink이며 보호된 releases 디렉터리 안의 승인된 이전 버전만 선택한다.

```bash
sudo systemctl stop parkeasy-staging
[[ "${PREVIOUS_RELEASE_ID:-}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || exit 1
test ! -e /srv/parkeasy/current.rollback && test ! -L /srv/parkeasy/current.rollback
test -f "/srv/parkeasy/releases/$PREVIOUS_RELEASE_ID/backend.jar"
test -f "/srv/parkeasy/releases/$PREVIOUS_RELEASE_ID/dist/index.html"
ln -s "/srv/parkeasy/releases/$PREVIOUS_RELEASE_ID" /srv/parkeasy/current.rollback
mv -Tf /srv/parkeasy/current.rollback /srv/parkeasy/current
sudo systemctl start parkeasy-staging
sudo nginx -t
sudo systemctl reload nginx
```

이전 JAR/dist로 health·nearby·detail·edge security를 재검증하고 브라우저 새로고침/asset 확인 후 트래픽을 복구한다. nginx 설정까지 변경했다면 보관한 이전 설정도 복구 후 `nginx -t`한다. current.rollback이 남아 있거나 이전 release가 없으면 중단하고 서비스를 철회한 상태로 유지한다.

**DB restore 조건:** 이번 단계는 migration/DB write가 없으므로 앱 오류만으로 DB를 복원하지 않는다. 향후 migration에서 비호환 schema, 부분 DDL 적용, 데이터 손상으로 이전 앱이 정상 동작하지 못할 때만 DBA가 복원을 결정한다. 새 schema와 이전 앱의 읽기 호환성이 검증되면 DB를 유지하고 앱만 rollback한다. migration 적용 후에는 이전 JAR 실행 가능성을 가정하지 않는다.

**별도 backup/restore 작업의 명령 예시 — 이번 단계 실행 금지:** DBA가 staging임을 확인하고 backup/restore 전용 client 설정 파일과 비밀 아닌 DB 식별자/보호 backup 경로를 주입한다. 대상 데이터베이스는 기존 DB를 덮지 않는 **격리된 빈 복구 DB**로 준비한다.

```bash
# 별도 backup 작업. SQL/오류를 터미널에 출력하지 않는다.
umask 077
mysqldump --defaults-extra-file=/run/secrets/parkeasy-mysql-backup.cnf \
  --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF \
  "$STAGING_DB_NAME" > "$BACKUP_FILE" 2> "$BACKUP_ERROR_FILE"
# exit 0, 파일 무결성, snapshot 시각 확인 후 암호화 저장. 빈 파일도 실패.

# 별도 restore 작업. 아래 cnf는 production과 분리된 복구 서버를 가리킨다.
mysql --defaults-extra-file=/run/secrets/parkeasy-mysql-restore.cnf \
  "$RESTORE_DB_NAME" < "$BACKUP_FILE" 2> "$RESTORE_ERROR_FILE"
```

InnoDB 기준 consistent dump 동안 concurrent DDL을 금지한다. routines/events 등 추가 DB 객체가 있다면 위 앱 테이블 중심 dump만으로 충분하지 않으므로 DBA가 snapshot 또는 전체 객체 복구 방식을 확정한다. [MySQL backup 문서](https://dev.mysql.com/doc/refman/8.0/en/mysqldump.html)를 참고한다.

복구 후 history/checksum/table/index/핵심 row count를 SELECT로 확인하고, 이전 앱을 격리된 복구 DB에 연결해 smoke를 수행한다. 복구 시점 이후 데이터 손실 범위와 RPO/RTO 수용 여부를 승인받은 뒤 연결 대상을 전환한다. 실패한 DB는 조사용으로 보존하며 임의 DROP/down migration/repair는 하지 않는다.

## 12. 완료 판정 / sign-off

각 항목에 PASS / FAIL / NOT RUN, 검증 시각, 담당자, **민감값을 제거한** 증적 위치를 기록한다. NOT RUN은 PASS가 아니며 필수 항목 하나라도 FAIL/NOT RUN이면 staging sign-off 불가다. 빈 데이터/관측 불가/기능을 구분할 데이터 부족도 NOT RUN이다.

| Gate | PASS 기준 | 현재 |
|---|---|---|
| Pre-deploy | staging 격리, ENV, artifact provenance/checksum, V13 검증, backup/restore 증적 | NOT RUN |
| Deploy | prod 단일 profile, Flyway/DDL/init/admin/scheduler OFF, 서비스 실행 확인 | NOT RUN |
| Health | 내부 및 HTTPS live/ready 200, 장애 주입 시 200/503 및 복구 | NOT RUN |
| API smoke | nearby/default/limit/세 정렬/detail, 각 status·JSON·데이터 검증 통과 | NOT RUN |
| UI smoke | 지도/검색/detail/정렬/상태표시/mobile/error/fallback 통과 | NOT RUN |
| Security | TLS, admin/legacy 404, CORS 허용/거부, 에러·로그 민감정보 미노출 | NOT RUN |
| Rollback | 이전 JAR/dist 복구 절차와 호환성 확인, 별도 restore 검증 증적 | NOT RUN |
| Sign-off | 아래 담당자 모두 승인, 미해결 필수 항목 0 | NOT RUN |

- [ ] 배포 담당자: release ID / 이전 release ID / 배포·복구 판단 시각 / 판정
- [ ] DB 담당자: schema version / backup ID / restore 증적 / migration 호환성 판정
- [ ] QA 담당자: API/UI/security 결과 / 데이터 부족 항목 / 판정
- [ ] 운영 책임자: 장애 대응·RPO/RTO / 최종 GO 또는 NO-GO / 시각

## 13. 최종 분류

**A. staging deploy ready:** JAR/dist 파일 존재, 소스상 prod 안전 설정, V1~V13 파일, 배포·proxy·검증·복구 명령이 준비됨. 실제 staging 환경 조건 충족을 전제로 실행 계획은 준비 완료다.

**B. staging deploy 후 확인 필요:** 실제 기동, TLS/nginx 문법·routing, ENV 주입, schema/history/checksum, health/API/UI/CORS, 로그 비노출, 격리 DB 장애 readiness 및 rollback 증적. 모두 현재 NOT RUN이다.

**C. production 전 blocker:** 실제 staging sign-off 부재, 배포 artifact의 commit/checksum 추적성, 실제 DB version·backup/restore·schema 호환성 미검증, secret manager/TLS/edge·로그 정책 미검증, health-aware 트래픽 제외와 모니터링/복구 책임·RPO/RTO 미확정. 현재 세션에는 Git CLI가 없어 변경 추적 검증도 미실시했다. production 진행 근거로 문서 존재만 사용하지 않는다.

문서 작성 시 검증: 실제 controller 경로/응답 필드/정렬 comparator/CORS/profile 설정과 대조했고, migration 파일이 V1~V13으로 연속 존재하며 boot JAR와 dist/index.html이 존재함을 확인했다. Markdown code fence 균형도 확인했다. Bash/nginx/jq 실행 도구는 현재 PATH에서 찾지 못했으므로 shell 실행·nginx 문법 검사·HTTP 검증은 수행하지 않았다. 앱을 기동하거나 DB에 접속하지 않았으며 artifact 최신성/내용 일치는 존재 확인과 별개로 pre-deploy에서 검증해야 한다.
