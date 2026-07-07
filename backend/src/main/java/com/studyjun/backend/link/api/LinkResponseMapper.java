package com.studyjun.backend.link.api;

import com.studyjun.backend.link.LinkResponse;
import com.studyjun.backend.link.application.ShortLinkResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LinkResponseMapper {

    private final String appBaseUrl;

    public LinkResponseMapper(@Value("${app.base-url:https://qwe123.shop}") String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }

    public LinkResponse.ShortLinkResponse toResponse(ShortLinkResult link) {
        return new LinkResponse.ShortLinkResponse(
                String.valueOf(link.id()),
                link.originalUrl(),
                link.shortCode(),
                buildShortUrl(link.shortCode()),
                link.createdAt(),
                link.active() ? "active" : "inactive",
                link.totalClicks(),
                link.ownerUserId() == null ? "anonymous" : String.valueOf(link.ownerUserId())
        );
    }

    private String buildShortUrl(String shortCode) {
        return appBaseUrl + "/api/s/" + shortCode;
    }
}
