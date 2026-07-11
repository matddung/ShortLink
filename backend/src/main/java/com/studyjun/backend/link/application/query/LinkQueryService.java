package com.studyjun.backend.link.application.query;

import com.studyjun.backend.link.AnonymousLinkExpiryPolicy;
import com.studyjun.backend.link.LinkStatsService;
import com.studyjun.backend.link.ShortLink;
import com.studyjun.backend.link.ShortLinkRepository;
import com.studyjun.backend.link.application.LinkStatsResult;
import com.studyjun.backend.link.application.ShortLinkResult;
import com.studyjun.backend.link.application.redirect.RedirectCacheInvalidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LinkQueryService {

    private final ShortLinkRepository shortLinkRepository;
    private final AnonymousLinkExpiryPolicy anonymousLinkExpiryPolicy;
    private final RedirectCacheInvalidator redirectCacheInvalidator;
    private final LinkStatsService linkStatsService;

    public LinkQueryService(ShortLinkRepository shortLinkRepository,
                            AnonymousLinkExpiryPolicy anonymousLinkExpiryPolicy,
                            RedirectCacheInvalidator redirectCacheInvalidator,
                            LinkStatsService linkStatsService) {
        this.shortLinkRepository = shortLinkRepository;
        this.anonymousLinkExpiryPolicy = anonymousLinkExpiryPolicy;
        this.redirectCacheInvalidator = redirectCacheInvalidator;
        this.linkStatsService = linkStatsService;
    }

    @Transactional
    public List<ShortLinkResult> getAnonymousLinks(String ownerKey) {
        List<ShortLink> links = shortLinkRepository.findAllByOwnerKeyAndOwnerUserIdIsNullOrderByCreatedAtDesc(ownerKey);

        List<ShortLink> expired = links.stream()
                .filter(this::isAnonymousExpired)
                .toList();
        if (!expired.isEmpty()) {
            shortLinkRepository.deleteAll(expired);
            redirectCacheInvalidator.invalidateAll(expired);
        }

        return links.stream()
                .filter(link -> !isAnonymousExpired(link))
                .map(this::toResult)
                .toList();
    }

    @Transactional
    public List<ShortLinkResult> getUserLinks(Long userId) {
        return shortLinkRepository.findAllByOwnerUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResult)
                .toList();
    }

    @Transactional
    public LinkStatsResult getLinkStats(Long linkId, Long userId) {
        return linkStatsService.getLinkStats(linkId, userId);
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