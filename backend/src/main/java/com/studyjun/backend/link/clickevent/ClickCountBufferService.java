package com.studyjun.backend.link.clickevent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ClickCountBufferService {

    private static final String CLICK_COUNT_KEY_PREFIX = "analytics:click-count:";
    private static final String FLUSH_LOCK_KEY = "analytics:flush-lock:click-count";
    private static final DefaultRedisScript<Long> RELEASE_FLUSH_LOCK_SCRIPT = new DefaultRedisScript<>(
            """
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    end
                    return 0
                    """,
            Long.class
    );

    private final RedisTemplate<String, String> redisTemplate;

    public ClickCountBufferService(@Qualifier("clickCountRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long increment(Long shortLinkId) {
        Long updatedValue = redisTemplate.opsForValue().increment(buildKey(shortLinkId));
        if (updatedValue == null) {
            throw new IllegalStateException("Redis INCR returned null for shortLinkId=" + shortLinkId);
        }
        return updatedValue;
    }

    public Set<String> findBufferedKeys() {
        Set<String> keys = redisTemplate.keys(CLICK_COUNT_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptySet();
        }
        return keys.stream()
                .filter(this::isBufferedCountKey)
                .collect(Collectors.toSet());
    }

    public Long consumeBufferedCount(String key) {
        String rawValue = redisTemplate.execute((RedisCallback<String>) connection -> {
            RedisStringCommands stringCommands = connection.stringCommands();
            byte[] serializedKey = serialize(key);
            byte[] raw = stringCommands.getDel(serializedKey);
            return deserialize(raw);
        });
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return Long.parseLong(rawValue);
    }

    public boolean tryAcquireFlushLock(String ownerToken, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(FLUSH_LOCK_KEY, ownerToken, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public void restoreBufferedCount(String key, long delta) {
        redisTemplate.opsForValue().increment(key, delta);
    }

    public void releaseFlushLock(String ownerToken) {
        redisTemplate.execute(RELEASE_FLUSH_LOCK_SCRIPT, List.of(FLUSH_LOCK_KEY), ownerToken);
    }

    public Long extractShortLinkId(String key) {
        if (!isBufferedCountKey(key)) {
            throw new IllegalArgumentException("Unexpected Redis click-count key: " + key);
        }
        return Long.parseLong(key.substring(CLICK_COUNT_KEY_PREFIX.length()));
    }

    private String buildKey(Long shortLinkId) {
        return CLICK_COUNT_KEY_PREFIX + shortLinkId;
    }

    private boolean isBufferedCountKey(String key) {
        if (key == null || !key.startsWith(CLICK_COUNT_KEY_PREFIX)) {
            return false;
        }

        String suffix = key.substring(CLICK_COUNT_KEY_PREFIX.length());
        return !suffix.isBlank() && suffix.chars().allMatch(Character::isDigit);
    }

    private byte[] serialize(String value) {
        return redisTemplate.getStringSerializer().serialize(value);
    }

    private String deserialize(byte[] value) {
        if (value == null) {
            return null;
        }
        return redisTemplate.getStringSerializer().deserialize(value);
    }
}