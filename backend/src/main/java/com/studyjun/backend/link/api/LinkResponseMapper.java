package com.studyjun.backend.link.api;

import com.studyjun.backend.link.application.LinkStatsResult;
import com.studyjun.backend.link.application.ShortLinkResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

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

    public List<LinkResponse.ShortLinkResponse> toShortLinkResponses(List<ShortLinkResult> links) {
        return links.stream()
                .map(this::toResponse)
                .toList();
    }

    public LinkResponse.LinkStatsResponse toResponse(LinkStatsResult stats) {
        return new LinkResponse.LinkStatsResponse(
                stats.totalClicks(),
                stats.uniqueClicks(),
                stats.lastClickedAt(),
                stats.referrers().stream()
                        .map(referrer -> new LinkResponse.ReferrerStat(
                                referrer.source(),
                                referrer.count(),
                                referrer.percentage()
                        ))
                        .toList(),
                stats.dailyClicks().stream()
                        .map(dailyClick -> new LinkResponse.DailyClickStat(
                                dailyClick.date(),
                                dailyClick.clicks()
                        ))
                        .toList(),
                stats.topCountries().stream()
                        .map(country -> new LinkResponse.CountryStat(
                                country.country(),
                                country.count()
                        ))
                        .toList()
        );
    }

    private String buildShortUrl(String shortCode) {
        return appBaseUrl + "/api/s/" + shortCode;
    }
}
