package com.studyjun.backend.link.domain;

public record ResolvedRedirectTarget(
        Long shortLinkId,
        String originalUrl
) {
}
