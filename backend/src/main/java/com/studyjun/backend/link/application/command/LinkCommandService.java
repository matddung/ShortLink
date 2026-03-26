package com.studyjun.backend.link.application.command;

import com.studyjun.backend.link.*;
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
    private final RedirectService redirectService;
    private final String appBaseUrl;
    private final long anonymousExpirationDays;

    public LinkCommandService(ShortLinkRepository shortLinkRepository,
                              AnonymousLinkExpiryPolicy anonymousLinkExpiryPolicy,
                              UrlValidationService urlValidationService,
                              ShortCodeService shortCodeService,
                              RedirectService redirectService,
                              @Value("${app.base-url:http://localhost:8080}") String appBaseUrl,
                              @Value("${app.anonymous.expiration-days:30}") long anonymousExpirationDays) {
        this.shortLinkRepository = shortLinkRepository;
        this.anonymousLinkExpiryPolicy = anonymousLinkExpiryPolicy;
        this.urlValidationService = urlValidationService;
        this.shortCodeService = shortCodeService;
        this.redirectService = redirectService;
        this.appBaseUrl = appBaseUrl;
        this.anonymousExpirationDays = anonymousExpirationDays;
    }

    @Transactional
    public LinkResponse.ShortLinkResponse createAnonymous(String originalUrl, String ownerKey) {
        urlValidationService.validate(originalUrl);

        String shortCode = shortCodeService.generateUniqueShortCode();
        Instant anonymousExpiresAt = Instant.now().plus(anonymousExpirationDays, ChronoUnit.DAYS);
        ShortLink saved = shortLinkRepository.save(new ShortLink(originalUrl, shortCode, ownerKey, anonymousExpiresAt));
        redirectService.invalidateRedirectLookupCache(saved.getShortCode());

        return toResponse(saved);
    }

    @Transactional
    public LinkResponse.ShortLinkResponse createForUser(String originalUrl, String customCode, Long userId) {
        urlValidationService.validate(originalUrl);

        String shortCode = shortCodeService.resolveShortCode(customCode);
        ShortLink shortLink = new ShortLink(originalUrl, shortCode, null, null);
        shortLink.claimToUser(userId);

        ShortLink saved = shortLinkRepository.save(shortLink);
        redirectService.invalidateRedirectLookupCache(saved.getShortCode());
        return toResponse(saved);
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
            expiredLinks.forEach(link -> redirectService.invalidateRedirectLookupCache(link.getShortCode()));
        }

        validLinks.forEach(link -> {
            link.claimToUser(userId);
            redirectService.invalidateRedirectLookupCache(link.getShortCode());
        });
        return validLinks.size();
    }

    @Transactional
    public long purgeExpiredAnonymousLinks() {
        Instant threshold = Instant.now();
        List<ShortLink> expiredLinks = shortLinkRepository.findAllByOwnerUserIdIsNullAndAnonymousExpiresAtBefore(threshold);
        expiredLinks.forEach(link -> redirectService.invalidateRedirectLookupCache(link.getShortCode()));
        return shortLinkRepository.deleteByOwnerUserIdIsNullAndAnonymousExpiresAtBefore(threshold);
    }

    private boolean isAnonymousExpired(ShortLink shortLink) {
        return anonymousLinkExpiryPolicy.isExpired(shortLink);
    }

    private LinkResponse.ShortLinkResponse toResponse(ShortLink shortLink) {
        return new LinkResponse.ShortLinkResponse(
                String.valueOf(shortLink.getId()),
                shortLink.getOriginalUrl(),
                shortLink.getShortCode(),
                buildShortUrl(shortLink.getShortCode()),
                shortLink.getCreatedAt(),
                shortLink.isActive() ? "active" : "inactive",
                shortLink.getTotalClicks(),
                shortLink.getOwnerUserId() == null ? "anonymous" : String.valueOf(shortLink.getOwnerUserId())
        );
    }

    private String buildShortUrl(String shortCode) {
        return appBaseUrl + "/s/" + shortCode;
    }
}