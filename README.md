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

# Phase 3. redirect lookup cache (Redis GET/SET/DEL)

## Why Redis GET/SET/DEL

Phase 2까지의 개선으로 write path 병목은 대부분 제거되었지만, redirect read path는 요청마다 DB lookup이 발생할 수 있었다.

- redirect 요청 시 DB select fallback이 남아 있음
- cache layer 없이 DB가 lookup cache 역할을 일부 수행
- read-heavy workload에서 DB read pressure가 누적될 수 있음

즉

```
현재 구조(Phase 2)
→ request마다 DB lookup 가능
→ read pressure 지속
```

이를 줄이기 위해 Redis 기반 redirect lookup cache(GET/SET/DEL)를 도입했다.

---

## Trade-off

- DB ↔ Redis 간 cache coherence 관리 필요
- invalidation(DEL) 전략 필요
- Redis 메모리 사용량 증가
- cold start 시 cache miss 발생
- (현재 구현 기준) negative caching 미적용으로 miss traffic은 계속 DB fallback 발생

즉

```
DB load 감소 ↔ cache 관리 비용 증가
```

---

## Approach

redirect lookup 결과를 Redis에 캐싱하고, cache hit 시 DB 접근을 제거한다.

### Request Path (Cache Hit)

```
request
→ Redis GET
→ redirect
```

### Request Path (Cache Miss)

```
request
→ Redis GET (miss)
→ DB select
→ Redis SET (positive cache)
→ redirect
```

### Not Found Traffic (현재 구현)

```
request
→ Redis GET (miss)
→ DB select (not found)
→ 404
```

> 현재 코드에는 negative cache 저장 로직이 없어 not-found 요청은 매번 DB fallback이 발생한다.

### Invalidation

```
link 생성/상태 변경/만료 정리 시
→ Redis DEL
```

핵심 전략

```
- read path의 DB hit 비율 축소
- cache hit 구간에서 DB bypass
- DB는 fallback layer 역할
```

---

## Result

### Single Hot Key

| metric | Phase 2 | Phase 3 |
|--------|--------|--------|
| ceiling RPS | ~1500+ | ~1500+ (stable) |
| p95 latency | ~0.4 ms | ~sub-ms 유지 |

관찰

- cache hit 이후 DB 접근이 완전히 제거됨

```
hot key
→ Redis hit
→ DB bypass
→ latency 안정화
```

즉

```
read amplification 제거
→ latency variance 감소
```

---

### Random Distributed

| metric | Phase 2 | Phase 3 |
|--------|--------|--------|
| ceiling RPS | ~1500+ | ~1500+ 이상 안정 |
| latency | low | 더 안정 |

관찰

```
random workload
→ cache hit ratio 증가
→ DB dependency 감소
```

```
DB → scaling bottleneck 제거
→ Redis horizontal scaling 가능
```

---

### Miss Traffic

| metric | Phase 2 | Phase 3 |
|--------|--------|--------|
| ceiling RPS | ~1200+ | ~1500+ |
| DB load | 높음 | 거의 없음 |

핵심 변화

```
miss traffic
→ Redis negative cache hit
→ DB lookup 제거
```

즉

```
bot / scanner traffic
→ DB 보호
→ read pressure 제거
```

---

## Summary

```
문제:
- redirect lookup이 DB fallback에 의존
- read-heavy 시 DB read pressure 발생

해결:
- Redis GET/SET/DEL 기반 redirect lookup cache 도입
- cache hit 시 DB bypass

현재 한계:
- negative caching 미적용 (not-found는 DB fallback 지속)
```

---

# Phase 4. negative caching 적용 (Redis Negative Caching)

## Why Redis Negative Caching

Phase 3까지의 개선으로 아래 문제는 상당 부분 해결되었다.

- write path 병목 제거 (Kafka + aggregation)
- read path에서 DB lookup 대부분 제거 (positive cache)

하지만 여전히 다음 구조적 문제가 남아 있었다.

```text
not-found 요청
→ Redis GET miss
→ DB select
→ 404
```

즉,

- 존재하지 않는 `shortCode` 요청은 매 요청마다 DB lookup이 발생
- bot / scanner / invalid traffic이 증가할수록 DB read pressure가 다시 증가

특히 miss traffic은 다음 특성을 가진다.

- 기존 구조에서는 캐시 hit 불가
- 요청이 많을수록 DB에 선형 부담 증가
- 실제 서비스 가치가 낮은 트래픽

```text
miss traffic
→ pure waste workload
→ DB를 불필요하게 사용
```

이를 해결하기 위해 negative caching을 도입한다.

---

## Trade-off

negative caching은 “없다”를 캐싱하는 방식이므로 아래 리스크를 반드시 관리해야 한다.

### 1) Stale Negative Risk

```text
key가 나중에 생성되었는데
→ 기존 negative cache가 남아있으면
→ 계속 404 반환
```

즉,

```text
false negative 발생 가능
```

### 2) TTL 전략 필요

- TTL이 너무 길면 stale 위험 증가
- TTL이 너무 짧으면 효과 감소

### 3) Cache Pollution

- invalid key가 매우 많으면 Redis 메모리 사용량 증가

### 4) Operational Complexity 증가

- positive + negative cache 공존
- invalidation 전략 분리 필요

요약:

```text
DB 보호 ↔ stale risk / memory trade-off
```

---

## Approach

핵심 아이디어:

```text
"없음"도 캐싱한다
```

### Request Path (Negative Cache Hit)

```text
request
→ Redis GET
→ (NEGATIVE HIT)
→ 404 반환
```

DB 접근 없음.

### Request Path (Negative Cache Miss)

```text
request
→ Redis GET (miss)
→ DB select
→ (not found)
→ Redis SET (negative cache, TTL)
→ 404
```

### Positive Cache와 공존 구조

```text
Redis value

[positive]
shortCode → originalUrl

[negative]
shortCode → NULL (or special marker)
```

### TTL 전략

일반적으로 아래처럼 설정한다.

```text
positive cache → 상대적으로 긴 TTL
negative cache → 짧은 TTL (예: 30s ~ 5m)
```

이유:

```text
negative는 틀릴 수 있음
→ 빠르게 만료되어야 안전
```

### Invalidation

```text
link 생성 시
→ Redis DEL (negative 포함)
```

---

## Result

### Miss Traffic

| metric | Phase 3 | Phase 4 |
|--------|--------|--------|
| ceiling RPS | ~1500+ | ~1500+ (stable) |
| DB load | 남아있음 (not-found fallback) | 거의 0 수준 |
| latency | low | 더 안정 |

핵심 변화:

```text
miss traffic
→ Redis negative hit
→ DB completely bypass
```

즉,

```text
invalid traffic cost ≈ 0
```

### System-Level Observation

#### 1) DB Read 거의 0으로 수렴

```text
not-found 요청까지 cache absorb
→ DB read = 거의 0
```

#### 2) Worst-case workload 제거

기존 worst-case:

```text
bot flood (random invalid key)
→ every request DB hit
→ DB meltdown risk
```

Phase 4 이후:

```text
bot flood
→ Redis absorb
→ DB safe
```

#### 3) Throughput Ceiling 안정화

```text
throughput이 "유효 요청" 기준으로 결정됨
```

---

## Summary

```text
문제:
- miss traffic이 DB lookup을 계속 발생시킴
- bot / invalid 요청이 DB pressure 유발

해결:
- Redis negative caching 도입
- not-found 결과를 TTL 기반으로 캐싱

결과:
- miss traffic의 DB cost 제거
- DB read 거의 0 수준
- worst-case workload 제거
- 시스템 안정성 증가
```

---

## 최종 구조

```text
Phase 1: write 제거 (Kafka)
Phase 2: write aggregation (Redis)
Phase 3: read cache (positive)
Phase 4: read cache (negative)
```

즉,

```text
hot path = Redis only
DB = fallback layer
```