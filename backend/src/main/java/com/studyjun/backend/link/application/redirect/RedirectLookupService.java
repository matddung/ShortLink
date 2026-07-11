package com.studyjun.backend.link.application.redirect;

import com.studyjun.backend.common.BusinessException;
import com.studyjun.backend.link.NegativeRedirectReason;
import com.studyjun.backend.link.RedirectLookupPolicy;
import com.studyjun.backend.link.RedirectLookupState;
import com.studyjun.backend.link.ResolvedRedirectTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
public class RedirectLookupService {

    private final RedirectTargetRepository redirectTargetRepository;
    private final RedirectTargetCache redirectTargetCache;
    private final NegativeRedirectCache negativeRedirectCache;
    private final RedirectLookupPolicy redirectLookupPolicy;
    private final RedirectMetrics redirectMetrics;
    private final RedirectCacheInvalidator redirectCacheInvalidator;

    public RedirectLookupService(RedirectTargetRepository redirectTargetRepository,
                                 RedirectTargetCache redirectTargetCache,
                                 NegativeRedirectCache negativeRedirectCache,
                                 RedirectLookupPolicy redirectLookupPolicy,
                                 RedirectMetrics redirectMetrics,
                                 RedirectCacheInvalidator redirectCacheInvalidator) {
        this.redirectTargetRepository = redirectTargetRepository;
        this.redirectTargetCache = redirectTargetCache;
        this.negativeRedirectCache = negativeRedirectCache;
        this.redirectLookupPolicy = redirectLookupPolicy;
        this.redirectMetrics = redirectMetrics;
        this.redirectCacheInvalidator = redirectCacheInvalidator;
    }

    public ResolvedRedirectTarget resolveRedirectableTarget(String shortCode) {
        Instant now = Instant.now();

        Optional<NegativeRedirectReason> negativeCachedReason = negativeRedirectCache.findByShortCode(shortCode);
        if (negativeCachedReason.isEmpty()) {
            redirectMetrics.incrementNegativeCacheMiss();
        }
        if (negativeCachedReason.isPresent()) {
            redirectMetrics.incrementNegativeCacheHit();
            log.info("Redirect negative cache hit. shortCode={}, reason={}", shortCode, negativeCachedReason.get());
            throw linkNotFoundException();
        }

        Optional<CachedRedirectTarget> cached = redirectTargetCache.findByShortCode(shortCode);

        if (cached.isPresent() && redirectLookupPolicy.evaluate(cached.get(), now) == RedirectLookupState.REDIRECTABLE) {
            redirectMetrics.incrementRedirectCacheHit();
            log.info("Redirect lookup cache hit. shortCode={}", shortCode);
            CachedRedirectTarget entry = cached.get();
            return new ResolvedRedirectTarget(entry.shortLinkId(), entry.originalUrl());
        }

        if (cached.isPresent()) {
            redirectCacheInvalidator.invalidate(shortCode);
        }

        redirectMetrics.incrementRedirectCacheMiss();
        log.info("Redirect lookup cache miss. shortCode={}", shortCode);
        redirectMetrics.incrementRedirectDbFallback();
        log.info("Redirect lookup DB fallback. shortCode={}", shortCode);

        Optional<RedirectTargetSnapshot> targetOptional = redirectTargetRepository.findByShortCode(shortCode);
        if (targetOptional.isEmpty()) {
            negativeRedirectCache.save(shortCode, NegativeRedirectReason.NOT_FOUND);
            throw linkNotFoundException();
        }

        RedirectTargetSnapshot target = targetOptional.get();

        RedirectLookupState redirectLookupState = redirectLookupPolicy.evaluate(target, now);
        if (redirectLookupState == RedirectLookupState.INACTIVE) {
            redirectCacheInvalidator.invalidate(shortCode);
            negativeRedirectCache.save(shortCode, NegativeRedirectReason.INACTIVE);
            throw linkNotFoundException();
        }

        if (redirectLookupState == RedirectLookupState.EXPIRED) {
            redirectTargetRepository.delete(target);
            redirectCacheInvalidator.invalidate(shortCode);
            negativeRedirectCache.save(shortCode, NegativeRedirectReason.EXPIRED);
            throw linkNotFoundException();
        }

        redirectTargetCache.save(
                shortCode,
                new CachedRedirectTarget(
                        target.shortLinkId(),
                        target.originalUrl(),
                        target.anonymousExpiresAt(),
                        target.active()
                )
        );
        negativeRedirectCache.delete(shortCode);

        return new ResolvedRedirectTarget(target.shortLinkId(), target.originalUrl());
    }

    private BusinessException linkNotFoundException() {
        return new BusinessException("LINK_NOT_FOUND", "링크를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
    }
}