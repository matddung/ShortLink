package com.studyjun.backend.analytics.infrastructure.optional.redis;

import com.studyjun.backend.infrastructure.optional.redis.RedisAvailability;
import com.studyjun.backend.common.observability.ShortLinkMetrics;
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
    private final RedisAvailability redisAvailability;
    private final ShortLinkMetrics shortLinkMetrics;

    public ClickCountBufferService(@Qualifier("clickCountRedisTemplate") RedisTemplate<String, String> redisTemplate,
                                   RedisAvailability redisAvailability,
                                   ShortLinkMetrics shortLinkMetrics) {
        this.redisTemplate = redisTemplate;
        this.redisAvailability = redisAvailability;
        this.shortLinkMetrics = shortLinkMetrics;
    }

    public long increment(Long shortLinkId) {
        if (!redisAvailability.isAvailable()) {
            throw new IllegalStateException("Redis is unavailable for click-count buffering");
        }

        try {
            Long updatedValue = redisTemplate.opsForValue().increment(buildKey(shortLinkId));
            if (updatedValue == null) {
                throw new IllegalStateException("Redis INCR returned null for shortLinkId=" + shortLinkId);
            }
            return updatedValue;
        } catch (RuntimeException ex) {
            redisAvailability.markUnavailable(ex);
            throw ex;
        }
    }

    public Set<String> findBufferedKeys() {
        if (!redisAvailability.isAvailable()) {
            shortLinkMetrics.setRedisCounterKeyCount(0);
            return Collections.emptySet();
        }

        Set<String> keys;
        try {
            keys = redisTemplate.keys(CLICK_COUNT_KEY_PREFIX + "*");
        } catch (RuntimeException ex) {
            redisAvailability.markUnavailable(ex);
            shortLinkMetrics.setRedisCounterKeyCount(0);
            return Collections.emptySet();
        }
        if (keys == null || keys.isEmpty()) {
            shortLinkMetrics.setRedisCounterKeyCount(0);
            return Collections.emptySet();
        }
        Set<String> bufferedKeys = keys.stream()
                .filter(this::isBufferedCountKey)
                .collect(Collectors.toSet());
        shortLinkMetrics.setRedisCounterKeyCount(bufferedKeys.size());
        return bufferedKeys;
    }

    public Long consumeBufferedCount(String key) {
        if (!redisAvailability.isAvailable()) {
            return null;
        }

        String rawValue;
        try {
            rawValue = redisTemplate.execute((RedisCallback<String>) connection -> {
                RedisStringCommands stringCommands = connection.stringCommands();
                byte[] serializedKey = serialize(key);
                byte[] raw = stringCommands.getDel(serializedKey);
                return deserialize(raw);
            });
        } catch (RuntimeException ex) {
            redisAvailability.markUnavailable(ex);
            return null;
        }
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return Long.parseLong(rawValue);
    }

    public boolean tryAcquireFlushLock(String ownerToken, Duration ttl) {
        if (!redisAvailability.isAvailable()) {
            return false;
        }

        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(FLUSH_LOCK_KEY, ownerToken, ttl);
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException ex) {
            redisAvailability.markUnavailable(ex);
            return false;
        }
    }

    public void restoreBufferedCount(String key, long delta) {
        if (!redisAvailability.isAvailable()) {
            return;
        }

        try {
            redisTemplate.opsForValue().increment(key, delta);
        } catch (RuntimeException ex) {
            redisAvailability.markUnavailable(ex);
        }
    }

    public void releaseFlushLock(String ownerToken) {
        if (!redisAvailability.isAvailable()) {
            return;
        }

        try {
            redisTemplate.execute(RELEASE_FLUSH_LOCK_SCRIPT, List.of(FLUSH_LOCK_KEY), ownerToken);
        } catch (RuntimeException ex) {
            redisAvailability.markUnavailable(ex);
        }
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
