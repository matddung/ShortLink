package com.studyjun.backend.link.application.redirect;

import com.studyjun.backend.common.BusinessException;
import com.studyjun.backend.link.domain.NegativeRedirectReason;
import com.studyjun.backend.link.domain.RedirectLookupPolicy;
import com.studyjun.backend.link.domain.RedirectLookupState;
import com.studyjun.backend.link.domain.ResolvedRedirectTarget;
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

        rejectNegativeCachedTarget(shortCode);

        return resolveFromPositiveCache(shortCode, now)
                .orElseGet(() -> resolveFromRepository(shortCode, now));
    }

    private void rejectNegativeCachedTarget(String shortCode) {
        Optional<NegativeRedirectReason> negativeCachedReason = negativeRedirectCache.findByShortCode(shortCode);
        if (negativeCachedReason.isEmpty()) {
            redirectMetrics.incrementNegativeCacheMiss();
        }
        if (negativeCachedReason.isPresent()) {
            redirectMetrics.incrementNegativeCacheHit();
            log.info("Redirect negative cache hit. shortCode={}, reason={}", shortCode, negativeCachedReason.get());
            throw linkNotFoundException();
        }
    }

    private Optional<ResolvedRedirectTarget> resolveFromPositiveCache(String shortCode, Instant now) {
        Optional<CachedRedirectTarget> cached = redirectTargetCache.findByShortCode(shortCode);
        if (cached.isPresent()
                && redirectLookupPolicy.evaluate(cached.get().active(), cached.get().anonymousExpiresAt(), now)
                == RedirectLookupState.REDIRECTABLE) {
            redirectMetrics.incrementRedirectCacheHit();
            log.info("Redirect lookup cache hit. shortCode={}", shortCode);
            CachedRedirectTarget entry = cached.get();
            return Optional.of(new ResolvedRedirectTarget(entry.shortLinkId(), entry.originalUrl()));
        }

        if (cached.isPresent()) {
            redirectCacheInvalidator.invalidate(shortCode);
        }

        redirectMetrics.incrementRedirectCacheMiss();
        log.info("Redirect lookup cache miss. shortCode={}", shortCode);
        return Optional.empty();
    }

    private ResolvedRedirectTarget resolveFromRepository(String shortCode, Instant now) {
        redirectMetrics.incrementRedirectDbFallback();
        log.info("Redirect lookup DB fallback. shortCode={}", shortCode);

        Optional<RedirectTargetSnapshot> targetOptional = redirectTargetRepository.findByShortCode(shortCode);
        if (targetOptional.isEmpty()) {
            return handleMissingTarget(shortCode);
        }

        RedirectTargetSnapshot target = targetOptional.get();

        RedirectLookupState redirectLookupState = redirectLookupPolicy.evaluate(target.active(), target.anonymousExpiresAt(), now);
        if (redirectLookupState == RedirectLookupState.INACTIVE) {
            return handleInactiveTarget(shortCode);
        }

        if (redirectLookupState == RedirectLookupState.EXPIRED) {
            return handleExpiredTarget(shortCode, target);
        }

        cacheRedirectableTarget(shortCode, target);
        return new ResolvedRedirectTarget(target.shortLinkId(), target.originalUrl());
    }

    private ResolvedRedirectTarget handleMissingTarget(String shortCode) {
        negativeRedirectCache.save(shortCode, NegativeRedirectReason.NOT_FOUND);
        throw linkNotFoundException();
    }

    private ResolvedRedirectTarget handleInactiveTarget(String shortCode) {
        redirectCacheInvalidator.invalidate(shortCode);
        negativeRedirectCache.save(shortCode, NegativeRedirectReason.INACTIVE);
        throw linkNotFoundException();
    }

    private ResolvedRedirectTarget handleExpiredTarget(String shortCode, RedirectTargetSnapshot target) {
        redirectTargetRepository.delete(target);
        redirectCacheInvalidator.invalidate(shortCode);
        negativeRedirectCache.save(shortCode, NegativeRedirectReason.EXPIRED);
        throw linkNotFoundException();
    }

    private void cacheRedirectableTarget(String shortCode, RedirectTargetSnapshot target) {
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
    }

    private BusinessException linkNotFoundException() {
        return new BusinessException("LINK_NOT_FOUND", "Link not found.", HttpStatus.NOT_FOUND);
    }
}
