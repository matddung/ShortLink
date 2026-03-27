package com.studyjun.backend.link;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ShortLinkMetrics {

    private final Counter redirectLookupCacheHitCounter;
    private final Counter redirectLookupCacheMissCounter;
    private final Counter redirectLookupNegativeCacheHitCounter;
    private final Counter redirectLookupNegativeCacheMissCounter;
    private final Counter redirectLookupDbFallbackCounter;
    private final Timer redirectLatencyTimer;

    private final Counter kafkaPublishSuccessCounter;
    private final Counter kafkaPublishFailureCounter;
    private final AtomicLong kafkaConsumerLag = new AtomicLong(0);

    private final AtomicInteger redisCounterKeyCount = new AtomicInteger(0);

    private final Counter flushExecutionCounter;
    private final Counter flushSuccessCounter;
    private final Counter flushFailureCounter;
    private final Counter flushDeltaTotalCounter;

    public ShortLinkMetrics(MeterRegistry meterRegistry) {
        this.redirectLookupCacheHitCounter = meterRegistry.counter("shortlink.redis.lookup.cache.hit.total");
        this.redirectLookupCacheMissCounter = meterRegistry.counter("shortlink.redis.lookup.cache.miss.total");
        this.redirectLookupNegativeCacheHitCounter = meterRegistry.counter("shortlink.redis.lookup.negative_cache.hit.total");
        this.redirectLookupNegativeCacheMissCounter = meterRegistry.counter("shortlink.redis.lookup.negative_cache.miss.total");
        this.redirectLookupDbFallbackCounter = meterRegistry.counter("shortlink.api.redirect.db_fallback.total");
        this.redirectLatencyTimer = Timer.builder("shortlink.api.redirect.latency")
                .description("Redirect API latency")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.kafkaPublishSuccessCounter = meterRegistry.counter("shortlink.kafka.publish.success.total");
        this.kafkaPublishFailureCounter = meterRegistry.counter("shortlink.kafka.publish.failure.total");
        Gauge.builder("shortlink.kafka.consumer.lag", kafkaConsumerLag, AtomicLong::doubleValue)
                .description("Observed consumer lag for click-event topic partition")
                .register(meterRegistry);

        Gauge.builder("shortlink.redis.counter_keys.count", redisCounterKeyCount, AtomicInteger::doubleValue)
                .description("Approximate number of buffered redis counter keys")
                .register(meterRegistry);

        this.flushExecutionCounter = meterRegistry.counter("shortlink.flush.execution.total");
        this.flushSuccessCounter = meterRegistry.counter("shortlink.flush.success.total");
        this.flushFailureCounter = meterRegistry.counter("shortlink.flush.failure.total");
        this.flushDeltaTotalCounter = meterRegistry.counter("shortlink.flush.delta.total");
    }

    public void incrementRedirectCacheHit() { redirectLookupCacheHitCounter.increment(); }
    public void incrementRedirectCacheMiss() { redirectLookupCacheMissCounter.increment(); }
    public void incrementNegativeCacheHit() { redirectLookupNegativeCacheHitCounter.increment(); }
    public void incrementNegativeCacheMiss() { redirectLookupNegativeCacheMissCounter.increment(); }
    public void incrementRedirectDbFallback() { redirectLookupDbFallbackCounter.increment(); }
    public Timer redirectLatencyTimer() { return redirectLatencyTimer; }

    public void incrementKafkaPublishSuccess() { kafkaPublishSuccessCounter.increment(); }
    public void incrementKafkaPublishFailure() { kafkaPublishFailureCounter.increment(); }
    public void setKafkaConsumerLag(long lag) { kafkaConsumerLag.set(Math.max(lag, 0)); }

    public void setRedisCounterKeyCount(int count) { redisCounterKeyCount.set(Math.max(count, 0)); }

    public void incrementFlushExecution() { flushExecutionCounter.increment(); }
    public void incrementFlushSuccess() { flushSuccessCounter.increment(); }
    public void incrementFlushFailure() { flushFailureCounter.increment(); }
    public void addFlushDelta(long delta) {
        if (delta > 0) {
            flushDeltaTotalCounter.increment(delta);
        }
    }
}