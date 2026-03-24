package com.studyjun.backend.link;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class RedirectLookupPolicy {

    public RedirectLookupState evaluate(ShortLink shortLink, Instant now) {
        if (shortLink.getOwnerUserId() == null
                && shortLink.getAnonymousExpiresAt() != null
                && !shortLink.getAnonymousExpiresAt().isAfter(now)) {
            return RedirectLookupState.EXPIRED;
        }
        return RedirectLookupState.REDIRECTABLE;
    }

    public RedirectLookupState evaluate(RedirectLookupCacheRepository.RedirectLookupCacheEntry cacheEntry, Instant now) {
        if (cacheEntry.anonymousExpiresAt() != null && !cacheEntry.anonymousExpiresAt().isAfter(now)) {
            return RedirectLookupState.EXPIRED;
        }
        return RedirectLookupState.REDIRECTABLE;
    }
}