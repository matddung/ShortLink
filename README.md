# Performance Test

## 테스트 환경

모든 테스트는 동일한 환경에서 수행되었다.

- OS: Windows 11
- CPU: AMD Ryzen 7 5700X (8 Core)
- Memory: 64GB
- JVM: OpenJDK 17
- PostgreSQL: 17
- HikariCP max pool size: 10
- Redis: 없음
- 테스트 위치: 로컬 단일 머신

> 로컬 환경에서 수행된 테스트이므로 실제 운영 환경 성능과는 차이가 있을 수 있음.

---

# 1. Single hot shortCode test

## 테스트 목적

동일한 `shortCode`로 트래픽이 집중되는 상황에서 redirect 경로의 처리 성능과 병목 가능성을 확인한다.

---

## 부하 시나리오

- 모든 요청이 **동일한 shortCode 하나로 집중**
- redirect 경로에서 발생하는 DB 접근 및 row contention 가능성 확인

---

## 부하 단계 결과

| 목표 RPS | 실제 처리 | 결과 |
|------|------:|------|
| 200 | ~200 | 안정 |
| 500 | ~500 | 안정 |
| 1000 | ~1000 | 안정 |
| 2000 | ~2000 | 안정 |
| 2200 | ~2010 ~ 2200 | 경계 구간 |
| 3000 | ~1352 | 실패 |

---

## 주요 성능 지표

### 2000 RPS

- avg: ~1.66 ms
- p95: ~2.36 ms
- p99: ~19.67 ms
- 실패율: 0%

### 2200 RPS (3회 반복)

#### 1차

- 실제 처리: ~2010 RPS
- avg: ~87.37 ms
- p95: ~659.98 ms
- p99: ~1.44 s
- dropped iterations: 8506

#### 2차

- 실제 처리: ~2171 RPS
- avg: ~16.41 ms
- p95: ~18.44 ms
- p99: ~503.49 ms
- dropped iterations: 1433

#### 3차

- 실제 처리: ~2200 RPS
- avg: ~2.61 ms
- p95: ~6.29 ms
- p99: ~27.42 ms
- dropped iterations: 없음

### 3000 RPS

- 실제 처리: ~1352 RPS
- avg: ~838.89 ms
- p95: ~1.52 s
- p99: ~1.78 s
- dropped iterations: 96784
- `Insufficient VUs` 발생

---

## DB 관측 결과

### Query count (`pg_stat_statements`)

| Query | Calls |
|------|------|
| short_links select | ~119k |
| link_click_events insert | ~119k |
| short_links update | ~119k |

redirect 요청 **1건당 약 3개의 DB 쿼리 발생**

```
request
 → short_links 조회
 → link_click_events insert
 → short_links update
 → HTTP redirect
```

---

## 시스템 리소스 관측

### CPU 사용량

| 상황 | App CPU | Postgres CPU |
|------|------:|------:|
| 평상시 | ~0% | ~0% |
| 2000 RPS | ~19% | ~7% |
| 2200 RPS | ~21% | ~8% |

### DB Connection (HikariCP)

| 상황 | Active connections |
|------|------:|
| 평상시 | 0 |
| 2000 RPS | 8 |
| 2200 RPS | 4 |

> 2200의 connection 수는 단일 시점 snapshot이므로 대표값으로 해석하기는 어렵다.

---

## 해석

### 캐시 부재

동일 shortCode 요청에도 매번 DB 조회가 발생한다.

```
shortCode -> originalUrl
```

lookup이 캐시 없이 매 요청마다 DB에서 수행된다.

### 클릭 로그 동기 저장

redirect 경로에서 `link_click_events` insert가 동기 수행된다.

즉 redirect hot path에서 write IO가 발생한다.

### Row hotspot

`short_links.total_clicks` update가 매 요청마다 수행되며 동일 row에 write contention이 발생한다.

특히 single hot key 시나리오에서 병목 후보가 된다.

### 경계 구간 존재

2200 RPS에서 결과 편차가 크게 발생했다.

가능한 원인

- JVM warmup
- OS / DB cache 상태
- connection reuse
- 로컬 머신 노이즈

즉 **2200 RPS는 절대 실패 지점이라기보다 경계 구간**으로 해석된다.

---

# 2. Distributed random test

## 테스트 목적

단일 hot key가 아닌 **여러 shortCode에 요청이 분산되는 read-heavy 상황**에서 redirect 경로의 처리 성능을 확인한다.

---

## 부하 시나리오

- `code001 ~ code100` 범위에서 랜덤 요청
- 요청 분산으로 row hotspot 제거
- read path scaling 관찰

---

## 부하 단계 결과

| 단계 | 목표 RPS | 실제 처리 | 결과 |
|------|------:|------:|------|
| phase_1_read | 600 | 600 | 안정 |
| phase_2_read | 1200 | 1200 | 안정 |
| phase_3_read | 1800 | 1800 | 안정 |

추가 관측

- total complete iterations: 216,003
- interrupted iterations: 0
- dropped iterations: 0

---

## 주요 성능 지표

전체 테스트 기준

- http_req_failed rate: 0
- checks rate: 100%
- http_req_duration p95: ~2.03 ms
- http_req_duration p99: ~2.81 ms

---

## DB 관측 결과

phase가 증가할수록 `short_links select` 호출 수가 증가한다.

즉 다음과 같은 패턴이 발생한다.

```
RPS 증가
 → short_links select 증가
 → DB read pressure 증가
```

---

## 시스템 리소스 관측

특별한 CPU saturation이나 connection saturation은 관찰되지 않았다.

---

## 해석

분산 시나리오에서는

- 600 / 1200 / 1800 RPS 모두 안정적으로 처리
- latency도 낮게 유지됨

즉 single hot key에서 발생하던 **row contention 문제가 제거된 상태**에서는 성능이 안정적으로 유지된다.

다만 read traffic이 계속 증가할 경우 DB read path가 병목으로 수렴할 가능성은 존재한다.

---

# 3. Miss load test

## 테스트 목적

존재하지 않는 `shortCode` 요청이 반복될 경우 **DB miss 조회가 누적되는지 확인**한다.

---

## 부하 시나리오

- 존재하지 않는 shortCode 반복 요청
- 404 반환 경로 테스트

---

## 부하 단계 결과

| 목표 RPS | 실제 처리 | 결과 |
|------|------:|------|
| 1200 | ~1200 | 안정 |

---

## 주요 성능 지표

1200 RPS 기준

- avg: ~1.06 ms
- p95: ~1.13 ms
- p99: ~1.57 ms
- 실패율: 0%
- checks: 100%

---

## DB 관측 결과 (`pg_stat_statements`)

| Query | Calls | mean_exec_time |
|------|------:|------:|
| select ... from short_links where short_code = $1 | 143,430 | 0.011 ms |
| link_click_events 관련 쿼리 | 0 | - |

특징

- 존재하지 않는 코드이므로
    - `link_click_events insert` 없음
    - `short_links update` 없음
- 하지만 `short_links select`는 요청 수만큼 반복 발생

---

## 해석

애플리케이션 레벨에서는 빠르게 404를 반환하지만 DB 레벨에서는 다음 흐름이 반복된다.

```
request
 → DB select
 → NOT FOUND
 → 404
```

즉 miss 트래픽도 DB read pressure를 유발한다.

---

# Identified Bottlenecks

성능 테스트를 통해 다음과 같은 구조적 병목 후보를 확인하였다.

### 1. redirect lookup DB 의존

현재 redirect 경로는 다음과 같이 동작한다.

```
request
 → short_links select
 → redirect
```

동일한 `shortCode` 요청에도 매번 DB 조회가 발생한다.  
따라서 트래픽이 증가할수록 DB read pressure가 선형적으로 증가한다.

특히 **single hot shortCode 시나리오에서는 동일 row 조회가 반복된다.**

---

### 2. 클릭 로그 동기 저장

redirect 처리 경로에서 `link_click_events insert`가 직접 수행된다.

```
request
 → insert link_click_events
 → redirect
```

즉 redirect hot path에 write IO가 포함되어 있다.

이 구조는 다음 문제를 만든다.

- write latency가 redirect latency에 직접 영향
- 트래픽 증가 시 DB write load 증가

---

### 3. total_clicks update hotspot

현재 구조에서는 redirect 요청마다 다음 update가 발생한다.

```
update short_links
set total_clicks = ?
```

single hot key 상황에서는 동일 row에 write contention이 발생할 수 있다.

---

### 4. miss 트래픽 증폭

존재하지 않는 `shortCode` 요청도 다음 흐름을 거친다.

```
request
 → DB select
 → NOT FOUND
 → 404
```

즉 miss 트래픽도 DB read pressure를 발생시킨다.

---

# Optimization Strategy

확인된 병목 후보를 기준으로 다음과 같은 개선 전략을 적용할 수 있다.

---

### 1. redirect lookup 캐싱

`shortCode -> originalUrl` 매핑을 Redis에 캐싱한다.

```
request
 → Redis lookup
 → redirect
```

효과

- DB read 감소
- hot shortCode 트래픽 완화
- redirect latency 감소

---

### 2. negative caching

존재하지 않는 shortCode도 캐싱한다.

```
shortCode -> NOT_FOUND
TTL: 짧게 (예: 30~60초)
```

효과

- miss 트래픽의 DB 전파 차단
- bot / scanner 트래픽 완화

---

### 3. 클릭 로그 비동기 처리

현재

```
request
 → insert link_click_events
 → redirect
```

개선

```
request
 → async event publish
 → redirect
consumer
 → log insert
```

방법

- message queue
- event stream
- async worker

효과

- redirect hot path write 제거
- latency 안정화

---

### 4. 클릭 카운터 분리

`total_clicks`를 매 요청마다 DB update 하지 않는다.

가능한 방식

```
Redis counter
 → periodic batch flush
 → DB update
```

또는

```
event log 기반 집계
```

효과

- row hotspot 제거
- DB write 감소