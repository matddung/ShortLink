package com.studyjun.backend.link.application.command;

import com.studyjun.backend.link.AnonymousLinkExpiryPolicy;
import com.studyjun.backend.link.ShortCodeService;
import com.studyjun.backend.link.ShortLink;
import com.studyjun.backend.link.ShortLinkRepository;
import com.studyjun.backend.link.UrlValidationService;
import com.studyjun.backend.link.application.ShortLinkResult;
import com.studyjun.backend.link.application.redirect.RedirectCacheInvalidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class LinkCommandService {

    private final ShortLinkRepository shortLinkRepository;
    private final AnonymousLinkExpiryPolicy anonymousLinkExpiryPolicy;
    private final UrlValidationService urlValidationService;
    private final ShortCodeService shortCodeService;
    private final RedirectCacheInvalidator redirectCacheInvalidator;
    private final long anonymousExpirationDays;

    public LinkCommandService(ShortLinkRepository shortLinkRepository,
                              AnonymousLinkExpiryPolicy anonymousLinkExpiryPolicy,
                              UrlValidationService urlValidationService,
                              ShortCodeService shortCodeService,
                              RedirectCacheInvalidator redirectCacheInvalidator,
                              @Value("${app.anonymous.expiration-days:30}") long anonymousExpirationDays) {
        this.shortLinkRepository = shortLinkRepository;
        this.anonymousLinkExpiryPolicy = anonymousLinkExpiryPolicy;
        this.urlValidationService = urlValidationService;
        this.shortCodeService = shortCodeService;
        this.redirectCacheInvalidator = redirectCacheInvalidator;
        this.anonymousExpirationDays = anonymousExpirationDays;
    }

    @Transactional
    public ShortLinkResult createAnonymous(String originalUrl, String ownerKey) {
        urlValidationService.validate(originalUrl);

        String shortCode = shortCodeService.generateUniqueShortCode();
        Instant anonymousExpiresAt = Instant.now().plus(anonymousExpirationDays, ChronoUnit.DAYS);
        ShortLink saved = shortLinkRepository.save(new ShortLink(originalUrl, shortCode, ownerKey, anonymousExpiresAt));
        redirectCacheInvalidator.invalidate(saved.getShortCode());

        return toResult(saved);
    }

    @Transactional
    public ShortLinkResult createForUser(String originalUrl, String customCode, Long userId) {
        urlValidationService.validate(originalUrl);

        String shortCode = shortCodeService.resolveShortCode(customCode);
        ShortLink shortLink = new ShortLink(originalUrl, shortCode, null, null);
        shortLink.claimToUser(userId);

        ShortLink saved = shortLinkRepository.save(shortLink);
        redirectCacheInvalidator.invalidate(saved.getShortCode());
        return toResult(saved);
    }

    @Transactional
    public int claimAnonymousLinks(String ownerKey, Long userId) {
        if (ownerKey == null || ownerKey.isBlank()) {
            return 0;
        }

        List<ShortLink> links = shortLinkRepository.findAllByOwnerKeyAndOwnerUserIdIsNullOrderByCreatedAtDesc(ownerKey);

        List<ShortLink> validLinks = links.stream()
                .filter(link -> !isAnonymousExpired(link))
                .toList();

        List<ShortLink> expiredLinks = links.stream()
                .filter(this::isAnonymousExpired)
                .toList();

        if (!expiredLinks.isEmpty()) {
            shortLinkRepository.deleteAll(expiredLinks);
            redirectCacheInvalidator.invalidateAll(expiredLinks);
        }

        validLinks.forEach(link -> {
            link.claimToUser(userId);
            redirectCacheInvalidator.invalidate(link.getShortCode());
        });
        return validLinks.size();
    }

    @Transactional
    public long purgeExpiredAnonymousLinks() {
        Instant threshold = Instant.now();
        List<ShortLink> expiredLinks = shortLinkRepository.findAllByOwnerUserIdIsNullAndAnonymousExpiresAtBefore(threshold);
        redirectCacheInvalidator.invalidateAll(expiredLinks);
        return shortLinkRepository.deleteByOwnerUserIdIsNullAndAnonymousExpiresAtBefore(threshold);
    }

    private boolean isAnonymousExpired(ShortLink shortLink) {
        return anonymousLinkExpiryPolicy.isExpired(shortLink);
    }

    private ShortLinkResult toResult(ShortLink shortLink) {
        return new ShortLinkResult(
                shortLink.getId(),
                shortLink.getOriginalUrl(),
                shortLink.getShortCode(),
                shortLink.getCreatedAt(),
                shortLink.isActive(),
                shortLink.getTotalClicks(),
                shortLink.getOwnerUserId()
        );
    }
}