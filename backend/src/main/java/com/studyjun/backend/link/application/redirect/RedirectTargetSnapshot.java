package com.studyjun.backend.link.application.redirect;

import java.time.Instant;

public record RedirectTargetSnapshot(
        Long shortLinkId,
        String originalUrl,
        Instant anonymousExpiresAt,
        boolean active
) {
}
