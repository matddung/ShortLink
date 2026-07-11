package com.studyjun.backend.link.application.redirect;

import java.time.Instant;

public record CachedRedirectTarget(
        Long shortLinkId,
        String originalUrl,
        Instant anonymousExpiresAt,
        Boolean active
) {
}
