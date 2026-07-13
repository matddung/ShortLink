package com.studyjun.backend.link.application.query;

import com.studyjun.backend.link.domain.AnonymousLinkExpiryPolicy;
import com.studyjun.backend.link.domain.ShortLink;
import com.studyjun.backend.link.infrastructure.persistence.ShortLinkRepository;
import com.studyjun.backend.link.application.ShortLinkResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LinkQueryService {

    private final ShortLinkRepository shortLinkRepository;
    private final AnonymousLinkExpiryPolicy anonymousLinkExpiryPolicy;

    public LinkQueryService(ShortLinkRepository shortLinkRepository,
                            AnonymousLinkExpiryPolicy anonymousLinkExpiryPolicy) {
        this.shortLinkRepository = shortLinkRepository;
        this.anonymousLinkExpiryPolicy = anonymousLinkExpiryPolicy;
    }

    @Transactional(readOnly = true)
    public List<ShortLinkResult> getAnonymousLinks(String ownerKey) {
        List<ShortLink> links = shortLinkRepository.findAllByOwnerKeyAndOwnerUserIdIsNullOrderByCreatedAtDesc(ownerKey);

        return links.stream()
                .filter(link -> !isAnonymousExpired(link))
                .map(this::toResult)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ShortLinkResult> getUserLinks(Long userId) {
        return shortLinkRepository.findAllByOwnerUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResult)
                .toList();
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
