package com.studyjun.backend.link.clickevent;

import java.util.UUID;

public record RedirectClickEventMessage(
        UUID eventId,
        String clickedAt,
        String requestId,
        String source,
        Long shortLinkId,
        String shortCode,
        String originalUrl,
        String countryCode,
        String referrer,
        String visitorKey
) {
}