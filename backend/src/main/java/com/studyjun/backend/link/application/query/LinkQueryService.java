package com.studyjun.backend.link.application.query;

import com.studyjun.backend.link.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LinkQueryService {

    private final ShortLinkRepository shortLinkRepository;
    private final AnonymousLinkExpiryPolicy anonymousLinkExpiryPolicy;
    private final RedirectService redirectService;
    private final LinkStatsService linkStatsService;
    private final String appBaseUrl;

    public LinkQueryService(ShortLinkRepository shortLinkRepository,
                            AnonymousLinkExpiryPolicy anonymousLinkExpiryPolicy,
                            RedirectService redirectService,
                            LinkStatsService linkStatsService,
                            @Value("${app.base-url:http://localhost:8080}") String appBaseUrl) {
        this.shortLinkRepository = shortLinkRepository;
        this.anonymousLinkExpiryPolicy = anonymousLinkExpiryPolicy;
        this.redirectService = redirectService;
        this.linkStatsService = linkStatsService;
        this.appBaseUrl = appBaseUrl;
    }

    @Transactional
    public List<LinkResponse.ShortLinkResponse> getAnonymousLinks(String ownerKey) {
        List<ShortLink> links = shortLinkRepository.findAllByOwnerKeyAndOwnerUserIdIsNullOrderByCreatedAtDesc(ownerKey);

        List<ShortLink> expired = links.stream()
                .filter(this::isAnonymousExpired)
                .toList();
        if (!expired.isEmpty()) {
            shortLinkRepository.deleteAll(expired);
            redirectService.invalidateRedirectLookupCaches(expired);
        }

        return links.stream()
                .filter(link -> !isAnonymousExpired(link))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<LinkResponse.ShortLinkResponse> getUserLinks(Long userId) {
        return shortLinkRepository.findAllByOwnerUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LinkResponse.LinkStatsResponse getLinkStats(Long linkId, Long userId) {
        return linkStatsService.getLinkStats(linkId, userId);
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