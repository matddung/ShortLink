# 테스트

## 개요

테스트는 로컬 단일 머신 환경에서 수행되었으며, 운영 환경 성능과 직접적으로 동일하지 않을 수 있습니다.

### 테스트 목적

동일 `shortCode`에 트래픽이 집중될 때 redirect 경로의 처리 성능과 병목 여부를 확인.

### 테스트 환경

- OS: Windows 11
- CPU: AMD Ryzen 7 5700X 8-Core
- Memory: 64GB
- JVM: OpenJDK 17
- PostgreSQL: 17
- HikariCP max pool size: 10
- Redis: 없음
- 테스트 위치: 로컬 단일 머신

### 측정 방법

성능 측정은 다음 도구를 사용하였다.

- 부하 생성: k6 (`constant-arrival-rate`)
- DB query count: PostgreSQL `pg_stat_statements`
- CPU 사용량: OS 프로세스 모니터링
- DB connection: Spring Boot Actuator (`hikaricp.connections.active`)

DB connection 수는 테스트 실행 중 Actuator endpoint를 통해 단일 시점 스냅샷으로 확인

# Single hot shortCode test

### 부하 단계 요약

| 목표 RPS |        실제 처리 | 결과           |
| ------ | -----------: | ------------ |
| 200    |         ~200 | 안정           |
| 500    |         ~500 | 안정           |
| 1000   |        ~1000 | 안정           |
| 2000   |        ~2000 | 안정           |
| 2200   | ~2010 ~ 2200 | 경계 구간, 편차 존재 |
| 3000   |        ~1352 | 실패           |

### 주요 성능 결과

#### 2000 RPS

* avg: ~1.66 ms
* p95: ~2.36 ms
* p99: ~19.67 ms
* 실패율: 0%
* 기능 체크: 100%

#### 2200 RPS 1차

* 실제 처리: ~2010 RPS
* avg: ~87.37 ms
* p95: ~659.98 ms
* p99: ~1.44 s
* dropped iterations: 8506
* 해석: 심한 성능 저하

#### 2200 RPS 2차

* 실제 처리: ~2171 RPS
* avg: ~16.41 ms
* p95: ~18.44 ms
* p99: ~503.49 ms
* dropped iterations: 1433
* 해석: 성공은 했지만 불안정

#### 2200 RPS 3차

* 실제 처리: ~2200 RPS
* avg: ~2.61 ms
* p95: ~6.29 ms
* p99: ~27.42 ms
* dropped iterations: 없음
* 해석: 안정적으로 성공

#### 3000 RPS

* 실제 처리: ~1352 RPS
* avg: ~838.89 ms
* p95: ~1.52 s
* p99: ~1.78 s
* dropped iterations: 96784
* `Insufficient VUs` 발생
* 해석: 실패

### CPU 사용량

| 상황       | App CPU | Postgres CPU |
| -------- | ------: | -----------: |
| 평상시      |     ~0% |          ~0% |
| 2000 RPS |    ~19% |          ~7% |
| 2200 RPS |    ~21% |          ~8% |

### DB connection 사용량 (HikariCP active)

| 상황       | Active connections |
| -------- | -----------------: |
| 평상시      |                  0 |
| 2000 RPS |                  8 |
| 2200 RPS |                  4 |

### DB query count (pg_stat_statements)

테스트 결과 redirect 요청 수와 거의 동일한 횟수의 쿼리가 발생하였다.

| Query | Calls |
|------|------|
| short_links select | ~119k |
| link_click_events insert | ~119k |
| short_links update | ~119k |

즉 redirect 요청 1건당 약 **3개의 DB 쿼리**가 발생하였다.

해석:

* 평상시에는 DB 커넥션 사용이 거의 없음
* 부하 시 active connection이 증가하는 것으로 보아 redirect hot path가 DB에 강하게 의존한다고 해석함
* 다만 2200의 `4`는 단일 시점 관측값이라 대표치로 보기 어렵고, 2200이 편차 큰 경계 구간이라는 점을 함께 해석해야 함
* CPU는 낮고 active connection은 증가하므로, CPU 계산보다는 DB 접근/대기/경합이 더 유력한 병목 후보

---

## 2. 문제

PostgreSQL `pg_stat_statements` 결과 기준으로 redirect 요청 1건당 DB 작업이 3개 발생함

* `select ... from short_links where short_code = ?`
* `insert into link_click_events ...`
* `update short_links set ... total_clicks = ?`

즉 구조는 다음과 같음.

```text
request
 -> short_links 조회
 -> link_click_events insert
 -> short_links update
 -> HTTP 302 redirect
```

문제점은 세 가지

첫째, 동일 shortCode 요청에도 매번 `short_links` 조회가 발생  
둘째, 클릭 로그를 redirect hot path에서 동기 insert 하고 있음  
셋째, 같은 `short_links` row를 매 요청마다 update 하면서 hotspot을 만들고 있음

---

## 3. 원인

### 캐시 부재

동일 shortCode 요청인데도 `short_links` select 호출 수가 요청 수와 거의 동일  
즉 `shortCode -> originalUrl` 조회가 캐시 없이 매번 DB에서 수행

### 클릭 로그 동기 저장

`link_click_events` insert가 요청 수만큼 증가  
redirect 응답 경로에서 로그 저장을 직접 처리하고 있다는 뜻

### 동일 row update hotspot

`short_links`의 같은 row에 대해 `total_clicks` update가 요청마다 발생  
같은 shortCode 하나에 요청을 몰아넣는 시나리오에서는 이 부분이 row contention의 핵심 후보

### 경계 구간의 높은 편차

2200 RPS에서 결과가 크게 흔들림

* 한 번은 p95/p99가 크게 악화
* 한 번은 성공했지만 p99가 높음
* 한 번은 비교적 안정적

이건 2200 RPS가 절대 실패 지점이라기보다, **로컬 환경 기준 경계 구간**임을 의미  
JVM 워밍업, DB/OS 캐시, 커넥션 재사용, 로컬 머신 잡음 차이에 따라 결과가 흔들릴 수 있습니다

### DB 의존성 확인

HikariCP active connections가 평상시 0에서 부하 시 증가  
즉 redirect 처리 경로가 DB 연결 점유와 직접 연결되어 있으며, 구조적으로 DB 의존도가 높음

---

# Distributed random test

## 테스트 목적

단일 hot shortCode 집중이 아닌, `code001 ~ code100`에 요청을 랜덤 분산시켜 read-heavy 상황에서 redirect 경로의 처리 성능과 DB read 부하 증가 여부를 확인

## 부하 단계 요약

| 단계 | 목표 RPS | 실제 처리 | 결과 |
| --- | ---: | ---: | --- |
| phase_1_read | 600 | 600.00 | 안정 |
| phase_2_read | 1200 | 1200.00 | 안정 |
| phase_3_read | 1800 | 1800.00 | 안정 |

추가 관측:

- total complete iterations: 216,003
- interrupted iterations: 0
- dropped iterations: 0

## 주요 성능 결과

### 전체(3분 합산)

* checks rate: 1 (100%)
* http_req_failed rate: 0
* http_req_duration p95: 2.0309 ms
* http_req_duration p99: 2.8132 ms

### phase별 처리 안정성

* phase_1_read: 1분 동안 600 iters/s 유지
* phase_2_read: 1분 동안 1200 iters/s 유지
* phase_3_read: 1분 동안 1800 iters/s 유지
* dropped iterations: 전체 구간에서 0

## 해석 가이드

- phase가 올라갈수록(`600 -> 1200 -> 1800`) `short_links select`의 `calls` 증가폭이 선형/비선형인지 확인
- p95/p99가 낮고 dropped iterations가 0이면 현재 구간은 안정 처리로 해석 가능
- throughput 증가 대비 DB read calls 증가율이 더 크면, DB read path 병목으로 수렴하는 신호로 해석 가능

---

즉, 분산 시나리오에서는 제공된 실행 로그 기준으로 **600/1200/1800 RPS를 모두 안정적으로 소화**했고, **p95/p99도 낮게 유지**  
다만 DB read 부하 누적 여부는 `pg_stat_statements` calls 추적을 함께 확인해 최종 판단해야 함