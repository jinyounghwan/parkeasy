# ParkEasy 브라우저 smoke test — 2026-09-10

실제 로컬 Chrome(headless), Vite 개발 서버, localhost:8080 및 기존 MySQL을 사용했다. 데스크톱 1440×900, 모바일 390×844에서 검사했다. Places/지도 SDK는 실제 Kakao 호출이다. fixture 검증은 아래에 별도로 표시했다. 전국 sync, DB 삭제, scheduler 활성화는 실행하지 않았다. 키가 들어간 SDK URL, 콘솔, HAR는 저장하지 않았다.

## 실행 중 backend 계약

- 8080 Java PID 8416, 시작 시각 2026-09-10 20:43:21 KST. Controller/Service 소스 수정 시각은 2026-09-08 21:26경이다.
- 첫 sandbox 연결 실패 후 sandbox 밖에서 실행 서버를 확인했다. 이 작업에서 IntelliJ 재시작을 수행하지 않았으며, 다음 실제 HTTP 결과로 요청한 최신 계약을 확인했다.
- 서울역 5km GET: limit 생략 200/100건, limit=300 200/300건, limit=301 400, 잘못된 sort 400.
- DISTANCE/FEE/AVAILABLE 모두 실제 UI에서 200 응답. frontend 요청은 항상 limit=100.

## 위치별 실제 사용자 흐름

| 장소 | 선택한 Places 후보 | nearby | 검색→선택→지도→목록→상세 |
|---|---|---:|---|
| 수원역 | 수원역 KTX | 100 | 데스크톱·모바일 통과 |
| 성남역 | 성남역 GTX-A | 100 | 데스크톱·모바일 통과 |
| 서울역 | 서울역 KTX | 100 | 데스크톱·모바일 통과 |
| 종로 | 종각역 | 100 | 모바일 검색·지도·상세 통과 |
| 판교 | 판교역 신분당선 | 100 | 모바일 검색·지도·상세 통과 |

실제 Maps marker 클릭→선택 정보창과 상세 버튼을 검증했다. 지도 드래그 자체에 자동 nearby 조회 기능은 없으며, 검색 후보를 선택하면 좌표를 변경하여 1회 재조회한다.

## 정렬·필터·Network

- DISTANCE: 반환 거리 오름차순 검사 통과.
- FEE: 무료→기본요금→정보 없음 순서 검사 통과. 다만 세 역 주변 목록의 요금 필드가 대부분 비어 있어 실제 데이터만으로 무료/유료 간 순서를 충분히 검증했다고 볼 수 없다. 기존 서비스 테스트에 누락 요금 마지막 테스트를 추가했다.
- AVAILABLE: 서울역의 실제 stale 19건이 잔여면수 내림차순으로 먼저 나오고 realtime 없는 건들이 뒤따른다. 실제 fresh 데이터는 없었다. fresh 0면→stale→realtime 없음 우선순위는 추가 서비스 테스트로 확인했다.
- frontend의 재정렬을 제거하여 backend 응답 순서를 목록·지도에 그대로 전달한다.
- 세 역의 공영/민영/무료/24시간 필터는 각각 **0건**. 실시간 정보 있음 필터는 수원역 0, 성남역 0, 서울역 19건. 빈 결과 UI를 실제 확인했다. 이것을 비어 있지 않은 필터 결과의 업무 정확성 검증으로 해석하면 안 된다.
- repository가 필터를 적용하고 service가 반경→정렬→limit을 적용하는 구조를 확인했다. 각 필터와 각 정렬의 조합도 UI 재조회로 검사한다.
- 장소 선택·정렬 변경·필터 변경마다 nearby **1회**. cluster 클릭·줌·주차장 선택·상세 열기는 nearby **0회**.
- React StrictMode가 활성화되어 있다. 초기 effect 재실행은 abort cleanup을 사용한다. 초기 geolocation 성공 시 관찰한 nearby는 1회였다. 사용자 액션에서 StrictMode나 로직 중복 호출은 관찰되지 않았다. 모든 환경에서 초기 개발 모드 요청이 항상 1회라는 보장은 아니다.
- Places는 submit 검색 방식이다. 문자 입력 중 요청 0회 확인. debounce를 추가하지 않았다.

예시(비밀값 없음):

```text
/api/parking-lots/nearby?latitude=37.55406888733184&longitude=126.97070335253385&radius=2000&limit=100&sort=AVAILABLE
```

## 상세·realtime·상태 UI

- 이름·주소·시설 총 주차면수 확인. 시설 총 주차면수는 이번에 패널에 추가했다. realtime 전체 면수와 시설 전체 면수는 서로 다른 원본 필드이므로 분리 표시한다.
- 수원민자역사(118825), 아름마을(185698), 부양빌딩(31238): 실제 realtime=null 및 운영 필드 다수 누락. 누락값은 정보 없음으로 표시.
- 서울역 주변 실제 realtime 19건: 수신 시각 2026-09-08 20:51:07, 전부 stale=true. 59면부터 0면까지 존재.
- 연세세브란스빌딩뒤 공영주차장(141): 실제 목록·marker·상세의 잔여 0면과 업데이트 지연 확인. 만차 표현 없음. 시설 면수와 realtime 전체 면수의 차이도 관찰되어 데이터 정합성 점검이 필요하다.
- 같은 141 상세에 기본 30분/3,000원, 추가 10분/0원, 일 최대 0원, 월 정기 250,000원, 평일 08:00~22:00, 토요일 09:00~22:00가 표시된다. 일요일·공휴일은 정보 없음이다. **동일 ID의 nearby baseFee는 null인데 operation.baseFee는 3000**으로 불일치를 확인했다. 시설 총 17면, realtime 총 2면도 서로 다르다.
- fixture: null, fresh 7면, stale 4면, fresh 0면의 상세 표시를 Chrome에서 추가 검증. DB 수정 없이 GET 응답만 해당 브라우저에서 대체했다.
- nearby/detail 각각 지연 응답과 HTTP 503 fixture로 loading→error 전환 검사. 실제 필터 0건으로 empty 검사. SDK/Places 실패 안내도 코드에 존재한다.

## marker UX 및 cluster 구조

수원역의 100개 marker가 2km 기본 지도에서 역 주변을 덮는 실제 화면을 확인하여 cluster가 필요하다고 판단했다. 서울역·성남역 적용 전 화면도 보관했다.

- 공식 SDK `libraries=services,clusterer`, `MarkerClusterer` 사용. 신규 앱 의존성 없음.
- `gridSize=60`, `minLevel=4`, `averageCenter=true`. 레벨 4 이상 cluster, 레벨 3 이하 개별 marker.
- 기본 cluster 클릭 확대 사용. 실제 클릭으로 레벨 6→5 확인.
- 회색 계열 단일 색 + 개수. 원래 marker는 기본 Kakao marker이며 realtime별 색 구분이 없었다. 이번에도 추가하지 않았다. 기존 정보창의 잔여/지연 텍스트 색은 유지했다.
- 선택 marker를 cluster에서 제거하여 지도에 별도 표시하고 zIndex=10, 이름 정보창, 지도 중심 이동으로 강조한다. 선택 변경 시 이전 marker는 cluster에 돌려놓는다.
- cluster는 지도 표현만 관리한다. 목록/응답 배열은 100건을 유지한다. 화면 밖 marker는 SDK가 표시하지 않을 수 있어 화면의 cluster 숫자 합이 항상 100은 아니다.
- ResizeObserver로 relayout 후 기존 중심을 복원한다. 모바일 상세는 fixed 패널로 표시하여 긴 목록에서 열어도 화면 안에 보인다. 검색 버튼 줄바꿈도 수정했다.

근거: [공식 cluster 예제](https://apis.map.kakao.com/web/sample/basicClusterer/), [공식 클릭 확대 예제](https://apis.map.kakao.com/web/sample/addClustererClickEvent/).

## 성능 및 제한

- 모바일 실제 응답 body를 읽은 뒤 목록 준비 및 두 animation frame까지: 수원 100ms, 성남 79ms, 서울 103ms, 종각 132ms, 판교 91ms. 네트워크 전체 왕복시간이나 지도 타일 완료 시간은 아니다.
- 별도 Chrome 계측에서 marker 생성자 누적 약 9~18ms(주차장 100 + 요청 중/완료 시 기준점 marker 2). paint/SDK tile 렌더 전체 시간을 뜻하지 않는다.
- 적용 전후 화면에서 겹침 감소가 명확하다. 동일 조건 렌더 시간 A/B 벤치마크는 하지 않았다.
- 선택/상세 액션이 지도를 재생성하지 않는 것을 확인했다. nearby loading으로 배열을 비우고 응답으로 채울 때 기존 effect가 지도를 두 번 생성하는 구조는 남아 있다. 장시간 드래그/반복 검색 프로파일링은 하지 않았다.

## 변경 및 검증

- `frontend/src/App.tsx`: URL 좌표 없는 첫 진입을 (0,0)으로 인식하는 오류 수정, frontend 재정렬 제거.
- `frontend/src/components/KakaoMap.tsx`: cluster, 선택 분리, resize 중심 보존.
- `frontend/src/kakaoSdk.ts`: cluster library/type, 지도 resize API type.
- `frontend/src/components/ParkingLotDetailPanel.tsx`: 시설 총 주차면수.
- `frontend/src/App.css`: 모바일 상세 위치, 검색 버튼 줄바꿈.
- `backend/src/test/java/com/parkeasy/backend/parking/service/ParkingLotServiceTest.java`: FEE 정보 없음, AVAILABLE fresh 0/stale/null 회귀 테스트.
- `.gitignore`: smoke 실행 도구 node_modules 제외.
- `.smoke-tools/`: 실제 Chrome 스크립트, JSON 결과 및 전후 화면. 앱 package.json/lock 변경 없음.

Backend `clean test integrationTest build` 통과(총 110 tests, 실패/오류 0). integrationTest는 기존 H2 기반 테스트이며 실제 MySQL 검증은 위 GET smoke가 담당한다. Frontend `npm run lint`, `npm run build` 통과.

재실행(프로젝트 루트, Chrome/실행 중 backend/frontend 필요):

```powershell
npm.cmd install --no-save --package-lock=false --prefix .smoke-tools playwright
node .smoke-tools/flows.cjs
node .smoke-tools/mobile.cjs
node .smoke-tools/advanced.cjs
```

`advanced.cjs`는 이 테스트 시점의 서울역 실제 데이터에 0면 사례가 있다는 전제도 검사한다. 데이터가 바뀌면 해당 전제를 다시 확인해야 한다.

## 기능 개발 완료 전 남은 우선순위

- **P0 — 데이터 정합성**: operation이 존재하는데 nearby 요금/필터 필드가 비어 있거나 false인 원인을 검증하고 검색·상세가 동일한 의미를 사용하도록 정리. 공영/민영/무료/24시간의 유효 결과 사례를 확보해 수용 테스트. 미상과 유료/비24시간을 구분할 필요가 있다. 시설/realtime 전체 면수 차이도 원본 의미와 매핑 확인.
- **P0 — 갱신 실행 제어**: 현재 설정 파일에 facility/operation scheduler 및 full-sync auto-resume true가 남아 있다. 이번에는 활성화/재시작하지 않았다. 이후 실행 프로필에서 의도치 않은 sync가 발생하지 않도록 명시적 opt-in 여부를 검토하고 별도 작업으로 정리.
- **P1 — 실제 fresh 데이터 수용 테스트**: 승인된 제한 범위의 갱신 정책/주기로 fresh·stale·null·0을 실데이터에서 함께 검증하고 수신 시각/지연 기준 확인. 전국 sync가 필요한지는 별도 판단.
- **P1 — 배포 환경 회귀**: 실제 배포 origin의 Kakao 허용 도메인, 위치 권한 거부, 네트워크 오류 복구 및 iOS/Android 실기기 확인. 현재 결과는 데스크톱 Chrome viewport 기반이다.
- **P2 — 지도 lifecycle 개선**: nearby 요청 중/완료에 map 재생성이 두 번 발생하는 부분을 분리하고 반복 검색·resize·선택의 가벼운 성능/누수 회귀 추가. 자동화 실행을 정식 테스트 명령에 편입하고 기존 README 실행/DB 설명을 갱신.
