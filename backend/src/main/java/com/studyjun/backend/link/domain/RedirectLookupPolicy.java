package com.studyjun.backend.link.domain;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class RedirectLookupPolicy {

    public RedirectLookupState evaluate(ShortLink shortLink, Instant now) {
        return shortLink.redirectLookupStateAt(now);
    }

    public RedirectLookupState evaluate(Boolean active, Instant anonymousExpiresAt, Instant now) {
        if (Boolean.FALSE.equals(active)) {
            return RedirectLookupState.INACTIVE;
        }

        if (anonymousExpiresAt != null && !anonymousExpiresAt.isAfter(now)) {
            return RedirectLookupState.EXPIRED;
        }
        return RedirectLookupState.REDIRECTABLE;
    }
}
