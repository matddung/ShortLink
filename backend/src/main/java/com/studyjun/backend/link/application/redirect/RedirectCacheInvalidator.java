package com.studyjun.backend.link.application.redirect;

import com.studyjun.backend.link.ShortLink;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedirectCacheInvalidator {

    private final RedirectTargetCache redirectTargetCache;
    private final NegativeRedirectCache negativeRedirectCache;

    public RedirectCacheInvalidator(RedirectTargetCache redirectTargetCache,
                                    NegativeRedirectCache negativeRedirectCache) {
        this.redirectTargetCache = redirectTargetCache;
        this.negativeRedirectCache = negativeRedirectCache;
    }

    public void invalidate(String shortCode) {
        redirectTargetCache.delete(shortCode);
        negativeRedirectCache.delete(shortCode);
    }

    public void invalidateAll(List<ShortLink> shortLinks) {
        shortLinks.stream()
                .map(ShortLink::getShortCode)
                .forEach(this::invalidate);
    }
}
