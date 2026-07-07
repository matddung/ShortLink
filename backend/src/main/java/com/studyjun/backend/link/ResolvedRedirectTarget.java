package com.studyjun.backend.link;

public record ResolvedRedirectTarget(
        Long shortLinkId,
        String originalUrl
) {
}
