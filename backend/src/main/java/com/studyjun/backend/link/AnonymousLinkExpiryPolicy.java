package com.studyjun.backend.link;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AnonymousLinkExpiryPolicy {

    private final RedirectLookupPolicy redirectLookupPolicy;

    public AnonymousLinkExpiryPolicy(RedirectLookupPolicy redirectLookupPolicy) {
        this.redirectLookupPolicy = redirectLookupPolicy;
    }

    public boolean isExpired(ShortLink shortLink) {
        return redirectLookupPolicy.evaluate(shortLink, Instant.now()) == RedirectLookupState.EXPIRED;
    }
}