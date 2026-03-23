# URL Shortener Redirect Performance Analysis

## Purpose

URL Shortener 서비스의 **redirect hot path 성능 특성**을 분석하기 위해 수행된 성능 테스트 결과를 정리한다.

redirect 경로는 서비스에서 **가장 높은 QPS가 발생하는 read-heavy endpoint**이며,  
특히 analytics write가 포함된 구조가 **처리량 ceiling에 어떤 영향을 미치는지** 확인하는 것이 테스트의 핵심 목적이다.

실험에서 검증하고자 한 질문은 다음과 같다.

1. read-only redirect 경로의 최대 처리량은 얼마인가
2. redirect 경로에서 analytics write가 포함되면 처리량은 어떻게 변하는가
3. redirect hot path에서 sync write가 구조적 병목이 되는가
4. workload 패턴 (hot key / distributed / miss)에 따라 병목 특성이 어떻게 달라지는가

이를 위해 redirect 경로를 두 가지로 분리하였다.

- **select-only redirect path (read-only baseline)**
- **full redirect path (production-like path)**

---

# Test Environment

모든 테스트는 동일한 환경에서 수행되었다.

| 항목 | 값 |
|-----|-----|
| OS | Windows 11 |
| CPU | AMD Ryzen 7 5700X (8 Core) |
| Memory | 64 GB |
| JVM | OpenJDK 17 |
| PostgreSQL | 17 |
| Connection Pool | HikariCP (max pool size: 10) |
| Test Location | 로컬 단일 머신 |

주의

테스트는 로컬 환경에서 수행되었기 때문에 절대적인 성능 수치보다  
**구조적 비교(structural comparison)** 에 의미가 있다.

---

# Redirect Path Design

## Select-only Redirect Path

redirect lookup 비용만 분리 측정하기 위해 **read-only redirect 경로**를 별도로 구현하였다.

```
request
→ short_links select
→ redirect
```

특징

- DB read만 수행
- analytics write 없음
- click event insert 없음
- total_clicks update 없음

즉 **redirect lookup 비용만 측정하기 위한 baseline 경로**이다.

## Full Redirect Path

현재 서비스 redirect 경로는 다음과 같이 동작한다.

```
request
→ short_links select
→ link_click_events insert
→ short_links total_clicks update
→ redirect
```

요청 1건당 **3개의 DB 쿼리**가 수행된다.

- short_links select
- link_click_events insert
- short_links update

이 구조에서는 다음과 같은 잠재적 병목이 존재한다.

- write latency
- row hotspot
- DB write pressure

---

# Workload Model

redirect 트래픽 특성을 모사하기 위해 다음 세 가지 workload를 테스트하였다.

## 1. Single Hot Key

모든 요청이 **동일한 shortCode 하나로 집중되는 상황**

```
/s/code000001
```

특징

- 동일 row 반복 접근
- row hotspot 발생 가능
- write contention 발생 가능

## 2. Distributed Random

여러 shortCode에 요청이 분산되는 상황

```
/s/code000001
/s/code000432
/s/code000874
...
```

특징

- hotspot 없음
- read scaling 관찰 가능

## 3. Miss Traffic

존재하지 않는 shortCode 요청

```
/s/unknownCode
```

특징

- 항상 NOT FOUND
- DB lookup 발생
- bot / scanner 트래픽 시뮬레이션

---

# Key Result Summary

핵심 결과는 다음과 같다.

| workload | select-only ceiling | full-path ceiling |
|--------|----------------|----------------|
| hot key | ~1100 RPS | ~400 RPS |
| random distributed | ~1500+ RPS | ~1200 RPS |
| miss traffic | ~1200+ RPS | ~1200 RPS |

핵심 관찰

- **hot key 상황에서 write contention 영향이 가장 큼**
- analytics write가 포함되면 **처리량 ceiling이 크게 감소**
- random workload에서는 read scaling이 비교적 안정적

---

# Single Hot Key Result

## Select-only

| target RPS | result |
|------------|--------|
| 200 | stable |
| 400 | stable |
| 600 | stable |
| 800 | stable |
| 1000 | stable |
| 1100 | stable |
| 1125 | collapse |

대표 latency (400 RPS)

p95 ≈ 7 ms  
p99 ≈ 8 ms

ceiling

```
1100 ~ 1125 RPS
```

## Full Path

| target RPS | result |
|------------|--------|
| 200 | stable |
| 400 | stable |
| 425 | collapse |

대표 latency (400 RPS)

p95 ≈ 15.9 ms

ceiling

```
400 ~ 425 RPS
```

## Hot Key Observation

full-path 처리량은 select-only의 약 **36 ~ 39 % 수준**이다.

즉

```
sync write
→ hotspot
→ throughput 감소
```

# Random Distributed Result

## Select-only

| RPS | result |
|-----|--------|
| 600 | stable |
| 1200 | near boundary |
| 1800 | collapse |

latency

p95 ≈ 1 ms

## Full Path

| RPS | result |
|-----|--------|
| 600 | stable |
| 1200 | boundary |
| 1400 | server crash |

## Random Observation

hot key보다 throughput이 높다.

이유

```
row hotspot 없음
→ write contention 감소
```

---

# Miss Traffic Result

## Select-only

1200 RPS에서도 안정

p95 ≈ 1 ms

## Full Path

1200 RPS에서 dropped iterations 발생

즉

```
miss traffic
→ DB lookup
→ read pressure 증가
```

---

# DB Query Observation

pg_stat_statements 관측 결과

## Select-only

```
short_links select ≈ request count
insert calls = 0
update calls = 0
```

## Full-path

```
short_links select ≈ request count
link_click_events insert ≈ request count
short_links update ≈ request count
```

즉 full-path는 요청당 **2개의 write**를 추가 수행한다.

# Structural Observations

실험을 통해 다음 구조적 특성을 확인하였다.

## 1. Redirect Hot Path는 Write에 매우 민감하다

```
select-only ceiling ≈ 1100
full-path ceiling ≈ 400
```

즉

```
sync analytics write
→ throughput 60% 감소
```

## 2. Row Hotspot은 성능을 크게 악화시킨다

single hot key 상황에서는 update short_links가 동일 row에 반복 수행되며 **write contention**이 발생한다.

## 3. Redirect Lookup은 DB에 강하게 의존한다

현재 구조

```
request
→ DB select
→ redirect
```

즉 DB가 **lookup cache 역할까지 수행하고 있다.**

## 4. Miss Traffic도 DB Pressure를 발생시킨다

존재하지 않는 shortCode도 DB lookup을 수행한다.

```
request
→ DB select
→ 404
```

---

# Conclusion

single hot key scenario 기준으로 다음 사실을 확인하였다.

```
select-only ceiling ≈ 1100 RPS
full-path ceiling ≈ 400 RPS
```

redirect hot path에서 수행되는 **sync analytics write가 처리량 ceiling을 약 60% 이상 감소**시키는 것을 확인하였다.

따라서 redirect 경로 성능 개선의 우선 전략은 다음과 같다.

1. analytics write async 처리
2. counter aggregation
3. redirect lookup cache 도입
4. negative caching 적용

---

# Phase 1. analytics write async 처리 (Kafka)

## Why Kafka

- backpressure 처리 (disk buffer)
- durability 보장
- consumer scale-out 가능
- batch write 가능

---

## Trade-off

- eventual consistency 발생
- 운영 복잡도 증가
- idempotency 필요

---

## Approach

analytics write를 request path에서 제거하고 Kafka로 비동기 처리

```text
[request thread]
request
→ short_links select
→ Kafka produce
→ redirect

[async consumer]
Kafka consume
→ link_click_events insert
→ short_links total_clicks update
```

---

## Result

### Single Hot Key

| metric | before | after |
|--------|--------|--------|
| ceiling RPS | ~400 | ~1200 |
| p95 latency | ~15 ms | ~0.5 ms |

실측:

```text
http_reqs ≈ 1200/s
p95 ≈ 0.56 ms
p99 ≈ 1.15 ms
```

---

## Summary

```text
문제: write가 hot path에 있음
해결: Kafka로 write 분리
결과: 400 → 1200 RPS (3배 증가)
```

---

# Phase 2. counter aggregation (Redis INCR)

## Why Redis INCR

Phase 1에서 Kafka를 통해 analytics write를 async로 분리했지만, 여전히 다음 문제가 남는다.

- `short_links total_clicks update`가 동일 row에 집중됨
- single hot key 상황에서 write contention 지속
- request 수에 비례하여 DB update가 발생 (write amplification)

즉

```
async 처리 이후에도
→ DB update hotspot은 여전히 존재
→ throughput ceiling의 잠재 병목
```

이를 해결하기 위해 per-request DB update를 제거하고, **Redis INCR 기반 counter aggregation 전략**을 도입하였다.

---

## Trade-off

- strong consistency → eventual consistency로 변경
- DB로 flush되기 전에 Redis에만 머물던 aggregate count는 유실될 수 있음
- flush 주기에 따라 DB 값이 지연 반영됨
- aggregator worker 운영 필요

---

## Approach

`total_clicks`를 즉시 DB에 반영하지 않고 Redis에 누적한 뒤, batch로 DB에 반영한다.

### Request Path

```
request
→ short_links select
→ Kafka produce
→ redirect
```

### Async Aggregation Path

```
[aggregator worker]

Kafka consume
→ link_click_events insert
→ Redis INCR
```

### Flush Worker

```
Redis counter read/getDel
→ aggregated `short_links total_clicks` update
```

---

## Result

### Single Hot Key

| metric | Phase 1 | Phase 2 |
|--------|--------|--------|
| ceiling RPS | ~1200 | ~1500+ |
| p95 latency | ~0.5 ms | ~0.4 ms |

실측 결과

```
http_reqs ≈ 1500+/s
p95 ≈ ~0.4 ms
p99 ≈ ~1 ms 이하
```

핵심 변화

```
Redis가 per-request aggregate update를 흡수하고, flush worker가 주기적으로 집계 반영
```

---

## Summary

```
문제: DB update hotspot + write amplification

해결: Redis INCR 기반 counter aggregation 도입

결과:
1200 → 1500+ RPS
latency 추가 감소
DB write pressure 감소
```

---