package com.studyjun.backend.link;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class NegativeRedirectLookupCacheRepository {

    private static final String KEY_PREFIX = "redirect:lookup:negative:";

    private final RedisTemplate<String, String> redisTemplate;
    private final Duration cacheTtl;

    public NegativeRedirectLookupCacheRepository(
            @Qualifier("clickCountRedisTemplate") RedisTemplate<String, String> redisTemplate,
            @Value("${app.redirect-cache.negative-ttl-seconds:60}") long cacheTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.cacheTtl = cacheTtlSeconds > 0 ? Duration.ofSeconds(cacheTtlSeconds) : Duration.ZERO;
    }

    public Optional<NegativeRedirectReason> findByShortCode(String shortCode) {
        String raw;
        try {
            raw = redisTemplate.opsForValue().get(buildKey(shortCode));
        } catch (RuntimeException ex) {
            log.warn("Failed to read negative redirect lookup cache entry from Redis. shortCode={}", shortCode, ex);
            return Optional.empty();
        }

        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(NegativeRedirectReason.valueOf(raw));
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown negative redirect lookup cache reason. shortCode={}, reason={}", shortCode, raw, ex);
            delete(shortCode);
            return Optional.empty();
        }
    }

    public void save(String shortCode, NegativeRedirectReason reason) {
        try {
            if (!cacheTtl.isZero() && !cacheTtl.isNegative()) {
                redisTemplate.opsForValue().set(buildKey(shortCode), reason.name(), cacheTtl);
            } else {
                redisTemplate.opsForValue().set(buildKey(shortCode), reason.name());
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to write negative redirect lookup cache entry to Redis. shortCode={}, reason={}", shortCode, reason, ex);
        }
    }

    public void delete(String shortCode) {
        try {
            redisTemplate.delete(buildKey(shortCode));
        } catch (RuntimeException ex) {
            log.warn("Failed to delete negative redirect lookup cache entry from Redis. shortCode={}", shortCode, ex);
        }
    }

    private String buildKey(String shortCode) {
        return KEY_PREFIX + shortCode;
    }
}