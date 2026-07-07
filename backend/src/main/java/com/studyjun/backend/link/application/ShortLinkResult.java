package com.studyjun.backend.link.application;

import java.time.Instant;

public record ShortLinkResult(
        Long id,
        String originalUrl,
        String shortCode,
        Instant createdAt,
        boolean active,
        long totalClicks,
        Long ownerUserId
) {
}
