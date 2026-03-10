package com.studyjun.backend.link;

import java.time.Instant;

public class LinkResponse {

    public record ShortLinkResponse(
            String id,
            String originalUrl,
            String shortCode,
            String shortUrl,
            Instant createdAt,
            String status,
            long totalClicks,
            String userId
    ) {
    }
}