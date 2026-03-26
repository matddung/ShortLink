package com.studyjun.backend.link;

import com.studyjun.backend.common.BusinessException;
import com.studyjun.backend.link.clickevent.ClickEventPublisher;
import com.studyjun.backend.link.clickevent.RedirectClickEventMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class RedirectService {

    private final ShortLinkRepository shortLinkRepository;
    private final ClickEventPublisher clickEventPublisher;
    private final RedirectLookupCacheRepository redirectLookupCacheRepository;
    private final NegativeRedirectLookupCacheRepository negativeRedirectLookupCacheRepository;
    private final RedirectLookupPolicy redirectLookupPolicy;
    private final Counter redirectCacheHitCounter;
    private final Counter redirectCacheMissCounter;
    private final Counter redirectDbFallbackCounter;

    public RedirectService(ShortLinkRepository shortLinkRepository,
                           ClickEventPublisher clickEventPublisher,
                           RedirectLookupCacheRepository redirectLookupCacheRepository,
                           NegativeRedirectLookupCacheRepository negativeRedirectLookupCacheRepository,
                           RedirectLookupPolicy redirectLookupPolicy,
                           MeterRegistry meterRegistry) {
        this.shortLinkRepository = shortLinkRepository;
        this.clickEventPublisher = clickEventPublisher;
        this.redirectLookupCacheRepository = redirectLookupCacheRepository;
        this.negativeRedirectLookupCacheRepository = negativeRedirectLookupCacheRepository;
        this.redirectLookupPolicy = redirectLookupPolicy;
        this.redirectCacheHitCounter = meterRegistry.counter("redirect.lookup.cache.hit.count");
        this.redirectCacheMissCounter = meterRegistry.counter("redirect.lookup.cache.miss.count");
        this.redirectDbFallbackCounter = meterRegistry.counter("redirect.lookup.db.fallback.count");
    }

    @Transactional
    public String resolveOriginalUrl(String shortCode, String countryCode, String referrer, String visitorKey, String requestId, String source) {
        ResolvedRedirectTarget redirectTarget = resolveRedirectableTarget(shortCode);

        Instant clickedAt = Instant.now();
        clickEventPublisher.publish(new RedirectClickEventMessage(
                buildEventId(redirectTarget.shortLinkId(), requestId),
                DateTimeFormatter.ISO_INSTANT.format(clickedAt),
                requestId,
                source,
                redirectTarget.shortLinkId(),
                shortCode,
                redirectTarget.originalUrl(),
                countryCode,
                referrer,
                visitorKey
        ));

        return redirectTarget.originalUrl();
    }

    private UUID buildEventId(Long shortLinkId, String requestId) {
        return UUID.nameUUIDFromBytes((shortLinkId + ":" + requestId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Transactional(readOnly = true)
    public String resolveOriginalUrlSelectOnly(String shortCode) {
        return resolveRedirectableTarget(shortCode).originalUrl();
    }

    public void invalidateRedirectLookupCache(String shortCode) {
        redirectLookupCacheRepository.delete(shortCode);
        negativeRedirectLookupCacheRepository.delete(shortCode);
    }

    public void invalidateRedirectLookupCaches(List<ShortLink> shortLinks) {
        shortLinks.stream()
                .map(ShortLink::getShortCode)
                .forEach(this::invalidateRedirectLookupCache);
    }

    private ResolvedRedirectTarget resolveRedirectableTarget(String shortCode) {
        Instant now = Instant.now();

        Optional<NegativeRedirectReason> negativeCachedReason = negativeRedirectLookupCacheRepository.findByShortCode(shortCode);
        if (negativeCachedReason.isPresent()) {
            redirectCacheHitCounter.increment();
            log.info("Redirect negative cache hit. shortCode={}, reason={}", shortCode, negativeCachedReason.get());
            throw linkNotFoundException();
        }

        Optional<RedirectLookupCacheRepository.RedirectLookupCacheEntry> cached = redirectLookupCacheRepository.findByShortCode(shortCode);

        if (cached.isPresent() && redirectLookupPolicy.evaluate(cached.get(), now) == RedirectLookupState.REDIRECTABLE) {
            redirectCacheHitCounter.increment();
            log.info("Redirect lookup cache hit. shortCode={}", shortCode);
            RedirectLookupCacheRepository.RedirectLookupCacheEntry entry = cached.get();
            return new ResolvedRedirectTarget(entry.shortLinkId(), entry.originalUrl());
        }

        if (cached.isPresent()) {
            invalidateRedirectLookupCache(shortCode);
        }

        redirectCacheMissCounter.increment();
        log.info("Redirect lookup cache miss. shortCode={}", shortCode);
        redirectDbFallbackCounter.increment();
        log.info("Redirect lookup DB fallback. shortCode={}", shortCode);

        Optional<ShortLink> shortLinkOptional = shortLinkRepository.findByShortCode(shortCode);
        if (shortLinkOptional.isEmpty()) {
            negativeRedirectLookupCacheRepository.save(shortCode, NegativeRedirectReason.NOT_FOUND);
            throw linkNotFoundException();
        }

        ShortLink shortLink = shortLinkOptional.get();

        RedirectLookupState redirectLookupState = redirectLookupPolicy.evaluate(shortLink, now);
        if (redirectLookupState == RedirectLookupState.INACTIVE) {
            invalidateRedirectLookupCache(shortCode);
            negativeRedirectLookupCacheRepository.save(shortCode, NegativeRedirectReason.INACTIVE);
            throw linkNotFoundException();
        }

        if (redirectLookupState == RedirectLookupState.EXPIRED) {
            shortLinkRepository.delete(shortLink);
            invalidateRedirectLookupCache(shortCode);
            negativeRedirectLookupCacheRepository.save(shortCode, NegativeRedirectReason.EXPIRED);
            throw linkNotFoundException();
        }

        redirectLookupCacheRepository.save(
                shortCode,
                new RedirectLookupCacheRepository.RedirectLookupCacheEntry(
                        shortLink.getId(),
                        shortLink.getOriginalUrl(),
                        shortLink.getAnonymousExpiresAt(),
                        shortLink.isActive()
                )
        );
        negativeRedirectLookupCacheRepository.delete(shortCode);

        return new ResolvedRedirectTarget(shortLink.getId(), shortLink.getOriginalUrl());
    }

    private record ResolvedRedirectTarget(Long shortLinkId, String originalUrl) {
    }

    private BusinessException linkNotFoundException() {
        return new BusinessException("LINK_NOT_FOUND", "링크를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
    }
}