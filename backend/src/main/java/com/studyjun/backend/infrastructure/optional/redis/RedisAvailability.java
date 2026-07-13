package com.studyjun.backend.infrastructure.optional.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class RedisAvailability {

    private final RedisConnectionFactory redisConnectionFactory;
    private final long retryCooldownMs;
    private final AtomicLong unavailableUntilMs = new AtomicLong(0);
    private final AtomicBoolean unavailableLogged = new AtomicBoolean(false);

    public RedisAvailability(RedisConnectionFactory redisConnectionFactory,
                             @Value("${app.redis.unavailable-retry-cooldown-ms:30000}") long retryCooldownMs) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.retryCooldownMs = retryCooldownMs;
    }

    public boolean isAvailable() {
        long now = System.currentTimeMillis();
        if (now < unavailableUntilMs.get()) {
            return false;
        }

        if (!unavailableLogged.get()) {
            return true;
        }

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.ping();
            unavailableLogged.set(false);
            log.info("Redis is available again; Redis-backed cache/counter features are enabled.");
            return true;
        } catch (RuntimeException ex) {
            markUnavailable(ex);
            return false;
        }
    }

    public void markUnavailable(RuntimeException ex) {
        unavailableUntilMs.set(System.currentTimeMillis() + retryCooldownMs);
        if (unavailableLogged.compareAndSet(false, true)) {
            log.warn("Redis is unavailable; Redis-backed cache/counter features are temporarily disabled. cause={}", ex.getMessage());
        }
    }
}
