package com.studyjun.backend.link.clickevent;

import com.studyjun.backend.link.ShortLinkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.analytics.kafka.consumer-enabled", havingValue = "true")
public class ClickCountFlushWorker {

    private final ClickCountBufferService clickCountBufferService;
    private final ShortLinkRepository shortLinkRepository;
    private final Duration flushLockTtl;

    public ClickCountFlushWorker(ClickCountBufferService clickCountBufferService,
                                 ShortLinkRepository shortLinkRepository,
                                 @org.springframework.beans.factory.annotation.Value("${app.analytics.flush.lock-ttl-ms:30000}") long flushLockTtlMs) {
        this.clickCountBufferService = clickCountBufferService;
        this.shortLinkRepository = shortLinkRepository;
        this.flushLockTtl = Duration.ofMillis(flushLockTtlMs);
    }

    @Scheduled(fixedDelayString = "${app.analytics.flush.fixed-delay-ms:5000}")
    @Transactional
    public void flush() {
        String lockToken = UUID.randomUUID().toString();
        if (!clickCountBufferService.tryAcquireFlushLock(lockToken, flushLockTtl)) {
            log.debug("Skipping click-count flush because another worker already holds the flush lock.");
            return;
        }

        try {
            Set<String> bufferedKeys = clickCountBufferService.findBufferedKeys();
            if (bufferedKeys == null || bufferedKeys.isEmpty()) {
                return;
            }

            for (String key : bufferedKeys) {
                flushSingleKey(key);
            }
        } finally {
            clickCountBufferService.releaseFlushLock(lockToken);
        }
    }

    private void flushSingleKey(String key) {
        Long delta = null;
        try {
            Long shortLinkId = clickCountBufferService.extractShortLinkId(key);
            delta = clickCountBufferService.consumeBufferedCount(key);

            if (delta == null || delta <= 0) {
                return;
            }

            int updatedRows = shortLinkRepository.incrementTotalClicks(shortLinkId, delta);
            if (updatedRows == 0) {
                log.warn("Skipping buffered click-count flush because short link was not found. shortLinkId={}, delta={}",
                        shortLinkId, delta);
                return;
            }

            log.info("Flushed buffered click counts. shortLinkId={}, delta={}", shortLinkId, delta);
        } catch (RuntimeException ex) {
            restoreBufferedCountOnFailure(key, delta, ex);
            log.error("Failed to flush buffered click-count key. key={}", key, ex);
        }
    }

    private void restoreBufferedCountOnFailure(String key, Long delta, RuntimeException originalException) {
        if (delta == null || delta <= 0) {
            return;
        }

        try {
            clickCountBufferService.restoreBufferedCount(key, delta);
            log.warn("Restored buffered click count after flush failure. key={}, delta={}", key, delta, originalException);
        } catch (RuntimeException restoreException) {
            log.error("Failed to restore buffered click count after flush failure. key={}, delta={}",
                    key, delta, restoreException);
        }
    }
}