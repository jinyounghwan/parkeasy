# ParkEasy

> 공공데이터 기반 현재 위치 주변 주차장 검색 서비스

사용자의 현재 위치를 기준으로 주변 주차장을 검색하고,  
운영정보·요금정보·실시간 정보를 한 화면에서 확인할 수 있도록 만든 웹 서비스입니다.

🔗 **Live Demo:** https://parkeasy.kr

---

## 프로젝트 소개

주차장 정보는 여러 데이터 항목이 분산되어 있고,  
시설마다 운영시간·요금·실시간 정보의 제공 여부도 다릅니다.

ParkEasy는 공공데이터를 수집·정제하여 하나의 서비스 모델로 통합하고,  
사용자가 지도에서 주변 주차장을 빠르게 탐색할 수 있도록 구현한 개인 프로젝트입니다.

단순 조회 화면 구현보다 다음 문제를 중점적으로 다뤘습니다.

- 대량 공공데이터 수집 및 저장
- 페이지 단위 동기화와 실패 복구
- 시설 / 운영정보 / 실시간 데이터 분리
- 데이터 결측 및 stale 데이터 처리
- 주변 주차장 조회 구조 개선
- 실제 공개 서버 배포 및 운영 환경 구성

---

## 주요 기능

### 현재 위치 기반 주변 주차장 검색

- 브라우저 Geolocation API를 이용한 현재 위치 조회
- 반경 기준 주변 주차장 검색
- 거리순 정렬

### Kakao Map 연동

- Kakao Maps JavaScript SDK
- 주차장 위치 마커 표시
- 마커 클러스터링
- 지도 이동 및 주차장 상세 연동

### 주차장 상세 정보

- 주차장 기본 정보
- 총 주차면수
- 기본 / 추가 요금
- 일 최대 요금
- 월 정기 요금
- 요일별 운영시간
- 실시간 정보 표시

### 데이터 상태 표현

원본 데이터가 제공하지 않는 값을 임의로 추론하지 않고  
`정보 없음` 상태로 명확하게 구분합니다.

실시간 데이터 역시 수집 시각을 기준으로 fresh / stale 상태를 구분할 수 있도록 설계했습니다.

### 반응형 UI

- Desktop
- Mobile Web
- 현재 위치 기능 지원

---

## 데이터 규모

현재 공개 서버 기준 주요 데이터는 다음과 같습니다.

| 데이터 | 건수 |
|---|---:|
| 주차장 시설 | 267,369 |
| 운영정보 | 267,359 |
| 실시간 정보 | 195 |

운영정보와 실시간 정보는 원본 공공데이터의 제공 범위 및 품질에 따라 일부 필드가 존재하지 않을 수 있습니다.

---

## Tech Stack

### Backend

- Java 17
- Spring Boot
- Spring Data JPA
- MySQL 8
- Flyway
- Gradle

### Frontend

- React
- TypeScript
- Vite
- Kakao Maps JavaScript SDK

### Infrastructure

- AWS Lightsail
- Ubuntu 24.04 LTS
- Nginx
- systemd
- Let's Encrypt
- Gabia DNS

---

## Architecture

```text
User Browser
     |
     | HTTPS
     v
parkeasy.kr
     |
     v
   Nginx
   /    \
  /      \
Frontend  /api/*
            |
            v
       Spring Boot
            |
            v
          MySQL

Spring Boot
     |
     v
공공데이터 API
```

---

## 핵심 구현

### 1. 대량 공공데이터 수집

전국 단위 주차장 데이터를 한 번에 처리하지 않고 페이지 단위로 수집하도록 구성했습니다.

- 페이지 단위 수집
- Batch 저장
- 수집 진행 상태 관리
- 실패 페이지 기록
- Retry
- 중단 후 재개
- 중복 데이터 처리

시설정보, 운영정보, 실시간 정보는 각각 독립된 동기화 흐름으로 관리합니다.

---

### 2. 데이터 품질 처리

공공데이터는 시설마다 제공되는 필드가 동일하지 않았습니다.

특히 다음 항목에서 결측 데이터가 다수 존재했습니다.

- 요금정보
- 운영시간
- 실시간 정보
- 일부 분류 정보

누락된 값을 임의의 기본값으로 변환하지 않고 실제 데이터와 미제공 데이터를 구분했습니다.

예를 들어 요금이 `0원`인 것과 요금정보가 제공되지 않은 것은 서로 다른 상태로 처리합니다.

```text
원본 데이터 미제공
        ↓
       null
        ↓
UI "정보 없음"
```

---

### 3. 조회 구조 개선

주변 주차장 목록을 조회할 때 각 주차장마다 운영정보와 실시간 정보를 개별 조회하면 조회 대상 수에 비례해 추가 Query가 발생할 수 있습니다.

이를 시설 ID 목록 기반의 Batch 조회 방식으로 변경했습니다.

```text
Before

ParkingLot 1
 ├─ Operation Query
 └─ Realtime Query

ParkingLot 2
 ├─ Operation Query
 └─ Realtime Query

...

After

ParkingLot 목록 조회
        +
Operation 일괄 조회
        +
Realtime 일괄 조회
```

이를 통해 주변 주차장 조회 시 불필요하게 Query 수가 증가하는 구조를 줄였습니다.

---

### 4. 실시간 데이터 상태 관리

실시간 데이터는 데이터가 존재한다는 사실만으로 현재 상태라고 판단하지 않습니다.

수집 시각을 기준으로 데이터 상태를 구분하여 오래된 데이터가 최신 정보처럼 노출되지 않도록 처리했습니다.

---

## Deployment

ParkEasy는 AWS Lightsail 환경에 직접 배포했습니다.

```text
Client
  |
HTTPS
  |
Nginx
  |
  +------ React Static Files
  |
  +------ /api/*
             |
             v
        Spring Boot
             |
             v
           MySQL
```

### 운영 환경

- AWS Lightsail
- Ubuntu 24.04 LTS
- Nginx Reverse Proxy
- Spring Boot systemd Service
- MySQL
- Gabia DNS
- Let's Encrypt HTTPS
- Static IP

외부에서는 `80`, `443` 포트만 웹 서비스 용도로 사용하며,  
Spring Boot의 `8080`과 MySQL의 `3306`은 직접 공개하지 않습니다.

---

## Health Check

Backend에는 서비스 상태 확인을 위한 endpoint를 구성했습니다.

```text
GET /api/health/live
GET /api/health/ready
```

실제 운영 서버에서 다음 결과를 확인했습니다.

```text
live  -> HTTP 200
ready -> HTTP 200
```

서버 재부팅 후에도 다음 서비스가 자동으로 정상 기동되는 것을 확인했습니다.

```text
mysql.service
nginx.service
parkeasy-backend.service
```

---

## 프로젝트 구조

```text
parkeasy/
├── backend/
│   ├── src/main/
│   ├── src/test/
│   ├── src/integrationTest/
│   ├── src/liveApiTest/
│   └── build.gradle
│
├── frontend/
│   ├── src/
│   └── package.json
│
├── docs/
│
└── README.md
```

---

## Local Development

### Backend

Java 17과 MySQL이 필요합니다.

```powershell
cd backend
.\gradlew.bat bootRun
```

주요 환경변수:

```text
PARKEASY_DB_URL
PARKEASY_DB_USERNAME
PARKEASY_DB_PASSWORD
PARKEASY_PUBLIC_DATA_SERVICE_KEY
```

실제 Secret 값은 Repository에 포함하지 않습니다.

### Frontend

```powershell
cd frontend
npm install
npm run dev
```

Kakao Maps 사용을 위해 로컬 환경변수가 필요합니다.

```text
VITE_KAKAO_MAP_KEY
```

---

## Build

### Backend

```powershell
cd backend
.\gradlew.bat clean test build
```

### Frontend

```powershell
cd frontend
npm run build
```

---

## 향후 개선

- 실시간 주차정보 수집 범위 확대
- 공영 / 민영 분류 데이터 보강
- 무료 주차장 데이터 품질 개선
- 24시간 운영정보 데이터 품질 개선
- CI/CD 구축
- 운영 모니터링
- DB 자동 백업
- PWA 적용
- 수집 품질 지표 고도화

---

## Security

다음 정보는 Git Repository에 포함하지 않습니다.

- DB Password
- 공공데이터 API Service Key
- Kakao JavaScript Key가 저장된 환경파일
- SSH Private Key
- Database Dump

운영 환경의 Secret은 환경변수와 서버 전용 설정 파일을 통해 관리합니다.

---

## Live Demo

**https://parkeasy.kr**