package com.studyjun.backend.link.domain;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AnonymousLinkExpiryPolicy {

    public boolean isExpired(ShortLink shortLink) {
        return shortLink.isAnonymousExpiredAt(Instant.now());
    }
}
