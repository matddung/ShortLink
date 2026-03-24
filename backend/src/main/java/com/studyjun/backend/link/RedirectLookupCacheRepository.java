package com.studyjun.backend.link;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
public class RedirectLookupCacheRepository {

    private static final String KEY_PREFIX = "redirect:lookup:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public RedirectLookupCacheRepository(
            @Qualifier("clickCountRedisTemplate") RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.redirect-cache.ttl-seconds:0}") long cacheTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = cacheTtlSeconds > 0 ? Duration.ofSeconds(cacheTtlSeconds) : Duration.ZERO;
    }

    public Optional<RedirectLookupCacheEntry> findByShortCode(String shortCode) {
        String raw;
        try {
            raw = redisTemplate.opsForValue().get(buildKey(shortCode));
        } catch (RuntimeException ex) {
            log.warn("Failed to read redirect lookup cache entry from Redis. shortCode={}", shortCode, ex);
            return Optional.empty();
        }

        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(raw, RedirectLookupCacheEntry.class));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to deserialize redirect lookup cache entry. shortCode={}", shortCode, ex);
            delete(shortCode);
            return Optional.empty();
        }
    }

    public void save(String shortCode, RedirectLookupCacheEntry entry) {
        try {
            String serialized = objectMapper.writeValueAsString(entry);
            if (!cacheTtl.isZero() && !cacheTtl.isNegative()) {
                redisTemplate.opsForValue().set(buildKey(shortCode), serialized, cacheTtl);
            } else {
                redisTemplate.opsForValue().set(buildKey(shortCode), serialized);
            }
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize redirect lookup cache entry. shortCode={}", shortCode, ex);
        } catch (RuntimeException ex) {
            log.warn("Failed to write redirect lookup cache entry to Redis. shortCode={}", shortCode, ex);
        }
    }

    public void delete(String shortCode) {
        try {
            redisTemplate.delete(buildKey(shortCode));
        } catch (RuntimeException ex) {
            log.warn("Failed to delete redirect lookup cache entry from Redis. shortCode={}", shortCode, ex);
        }
    }

    private String buildKey(String shortCode) {
        return KEY_PREFIX + shortCode;
    }

    public record RedirectLookupCacheEntry(Long shortLinkId, String originalUrl, Instant anonymousExpiresAt) {
    }
}