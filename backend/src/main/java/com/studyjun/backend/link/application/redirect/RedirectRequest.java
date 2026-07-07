package com.studyjun.backend.link.application.redirect;

public record RedirectRequest(
        String shortCode,
        String countryCode,
        String referrer,
        String visitorKey,
        String requestId,
        String source
) {
}
