package com.studyjun.backend.link.infrastructure.cache;

import com.studyjun.backend.config.RedisAvailability;
import com.studyjun.backend.link.NegativeRedirectReason;
import com.studyjun.backend.link.application.redirect.NegativeRedirectCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class RedisNegativeRedirectCache implements NegativeRedirectCache {

    private static final String KEY_PREFIX = "redirect:lookup:negative:";

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisAvailability redisAvailability;
    private final Duration cacheTtl;

    public RedisNegativeRedirectCache(
            @Qualifier("clickCountRedisTemplate") RedisTemplate<String, String> redisTemplate,
            RedisAvailability redisAvailability,
            @Value("${app.redirect-cache.negative-ttl-seconds:60}") long cacheTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.redisAvailability = redisAvailability;
        this.cacheTtl = cacheTtlSeconds > 0 ? Duration.ofSeconds(cacheTtlSeconds) : Duration.ZERO;
    }

    @Override
    public Optional<NegativeRedirectReason> findByShortCode(String shortCode) {
        if (!redisAvailability.isAvailable()) {
            return Optional.empty();
        }

        String raw;
        try {
            raw = redisTemplate.opsForValue().get(buildKey(shortCode));
        } catch (RuntimeException ex) {
            redisAvailability.markUnavailable(ex);
            log.debug("Skipped negative redirect lookup cache read after Redis failure. shortCode={}", shortCode);
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

    @Override
    public void save(String shortCode, NegativeRedirectReason reason) {
        if (!redisAvailability.isAvailable()) {
            return;
        }

        try {
            if (!cacheTtl.isZero() && !cacheTtl.isNegative()) {
                redisTemplate.opsForValue().set(buildKey(shortCode), reason.name(), cacheTtl);
            } else {
                redisTemplate.opsForValue().set(buildKey(shortCode), reason.name());
            }
        } catch (RuntimeException ex) {
            redisAvailability.markUnavailable(ex);
            log.debug("Skipped negative redirect lookup cache write after Redis failure. shortCode={}, reason={}", shortCode, reason);
        }
    }

    @Override
    public void delete(String shortCode) {
        if (!redisAvailability.isAvailable()) {
            return;
        }

        try {
            redisTemplate.delete(buildKey(shortCode));
        } catch (RuntimeException ex) {
            redisAvailability.markUnavailable(ex);
            log.debug("Skipped negative redirect lookup cache delete after Redis failure. shortCode={}", shortCode);
        }
    }

    private String buildKey(String shortCode) {
        return KEY_PREFIX + shortCode;
    }
}
