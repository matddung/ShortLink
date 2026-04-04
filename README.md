# 🚀 ShortLink

> 대규모 트래픽에서 병목이 되던 단축 URL 리다이렉트 경로를, **Kafka + Redis 기반 비동기/캐시 아키텍처**로 재구성해 처리량과 응답 속도를 개선한 URL Shortener 프로젝트

---

## 📌 프로젝트 개요

* 개발 기간: 성능 개선 실험 및 단계별 고도화 진행 (Phase 1 ~ 4)
* 팀 구성: 1인
* 역할: 백엔드 설계/구현, 성능 테스트 설계 및 분석, 캐시/이벤트 파이프라인 도입
* 기술 스택:
    * Frontend: Next.js, TypeScript
    * Backend: Spring Boot (Java 17)
    * Database: PostgreSQL
    * Infra/Data: Redis, Kafka, Docker Compose, Prometheus, Alertmanager, Nginx

---

## ❗ 문제 정의 (Why)

이 프로젝트는 URL Shortener 서비스에서 가장 트래픽이 높은 리다이렉트 경로의 성능 한계를 해결하기 위해 시작했다.

* 기존 방식의 한계:
    * 리다이렉트 요청 1건마다 `SELECT + INSERT + UPDATE`가 동기적으로 수행되어 DB write pressure가 높았음
    * hot key(특정 shortCode 쏠림) 상황에서 row hotspot과 write contention이 크게 발생했음
* 사용자가 겪는 불편:
    * 고부하 시 응답 지연 증가
    * 처리량 ceiling이 낮아 트래픽 급증 상황에서 안정성 저하
* 해결 목표:
    * 리다이렉트 hot path에서 동기 write 제거
    * DB 의존도를 낮추고 캐시 중심 경로로 전환
    * miss traffic(존재하지 않는 코드 요청)으로 인한 낭비성 DB 부하 차단

---

## 💡 해결 방법 (How)

### 핵심 아이디어

리다이렉트 경로를 **"요청 즉시 응답"**과 **"분석/집계 비동기 처리"**로 분리하고,
lookup을 Redis 중심으로 전환해 DB를 fallback layer로만 사용했다.

### 시스템 구조

1. **Phase 1**: Kafka 기반 비동기 이벤트 전환 (요청 경로에서 analytics write 제거)
2. **Phase 2**: Redis INCR 기반 카운터 집계 후 배치 flush
3. **Phase 3**: Redis Positive Cache로 리다이렉트 lookup 캐시
4. **Phase 4**: Redis Negative Cache로 not-found 트래픽 흡수

### 주요 기능

* 기능 1: 단축 URL 생성/관리 및 리다이렉트
* 기능 2: 클릭 이벤트 수집, 통계 집계, 대시보드 조회
* 기능 3: 캐시(positive/negative) 기반 저지연 리다이렉트
* 기능 4: hot key/miss traffic 대응을 위한 성능 최적화

---

## 🏗️ 아키텍처

* Frontend:
    * Next.js 기반 사용자 페이지 및 대시보드
* Backend:
    * Spring Boot API 서버
    * Kafka Producer/Consumer 기반 클릭 이벤트 파이프라인
    * Redis cache + counter aggregation worker
* Database:
    * PostgreSQL (원본 데이터 저장 및 배치 반영)

아키텍처 요약:

```text
[Client]
   ↓
[Frontend (Next.js)]
   ↓
[Backend API (Spring Boot)]
   ├─ Redirect Lookup: Redis(GET) → (miss 시) PostgreSQL
   ├─ Click Event: Kafka Produce
   └─ Link Command/Query API

[Kafka Consumer]
   ├─ link_click_events 적재
   └─ Redis INCR 집계

[Flush Worker]
   └─ Redis 집계값을 PostgreSQL에 배치 반영
```

---

## 🧠 기술적 의사결정 (중요)

### 기술 선택 이유

* **Kafka**: 요청 경로에서 write를 분리해 latency/throughput 병목을 제거하기 위해 선택
* **Redis INCR**: per-request DB update를 없애고 hot key 카운터를 메모리에서 흡수하기 위해 선택
* **Redis Positive/Negative Cache**: read-heavy 및 miss-heavy 트래픽에서 DB fallback 비율을 최소화하기 위해 선택

### 대안 비교

* Kafka 대신 DB 비동기 큐 테이블 사용:
    * 장점: 구성 단순
    * 단점: 결국 DB write pressure를 완전히 제거하지 못함
* Redis 없이 DB 인덱스 튜닝 중심 접근:
    * 장점: 운영 구성 단순
    * 단점: 구조적 병목(동기 write, miss traffic DB hit) 해결이 제한적

### 트레이드오프

* strong consistency 일부 포기 (eventual consistency 허용)
* 캐시 정합성/TTL/무효화 전략 운영 복잡도 증가
* Kafka/Redis 운영 비용 추가

---

## ⚡ 트러블슈팅

### 문제 상황

single hot key 환경에서 full-path 처리량이 빠르게 붕괴하고 p95 latency가 상승.

### 원인 분석

* 리다이렉트 요청마다 동기 write가 발생
* 동일 row에 대한 update 집중으로 write contention 발생

### 해결 방법

1. analytics write를 Kafka로 분리 (비동기화)
2. Redis INCR 집계로 DB update 빈도 축소
3. redirect lookup cache 및 negative cache 도입

### 결과

* 요청 경로에서 동기 write를 제거해 hot key 구간의 병목을 완화
* Redis 캐시/집계 도입 후 DB는 fallback + 배치 반영 중심으로 역할 축소
* miss 트래픽은 negative caching으로 흡수해 DB read 낭비를 크게 감소

---

## 📊 성과 (Impact)

* 성능 개선 (README 기준 실측값, 로컬 단일 머신 환경):
    * Single Hot Key (Full Path)
        * 초기: 약 **400 RPS**
        * Phase 1(Kafka 비동기화): 약 **1200 RPS** → **약 200% 상승(3배)**
        * Phase 2~4 포함 최종: 약 **1500+ RPS** → 초기 대비 **약 275% 상승(3.75배 수준)**
    * Hot Key에서 Select-only 대비 Full Path 격차
        * 초기: Select-only 약 **1100 RPS** vs Full Path 약 **400 RPS** → **약 700 RPS 차이** (Full Path가 약 **63.6% 낮음**)
        * 개선 후: Full Path 약 **1500+ RPS**로, 초기 Select-only(1100 RPS) 기준 대비 **최소 400 RPS 이상 상회**
    * p95 latency
        * 초기(Full Path): 약 **15.9ms**
        * 개선 후: **sub-ms ~ 1ms 내외** → 대략 **90%+ 감소**
    * [성능 테스트 문서 보기](./README_test.md)
* 배포/운영 관점 결과:
    * Redis/Kafka 기반으로 트래픽 스파이크 시 응답 안정성 개선
    * miss/bot 트래픽 유입 시 DB 보호 효과 강화
    * 단, 위 수치는 로컬 테스트 기준이므로 실제 운영에서는 네트워크/인프라 조건에 따라 달라질 수 있음