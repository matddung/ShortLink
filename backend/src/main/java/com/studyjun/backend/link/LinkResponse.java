package com.studyjun.backend.link;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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

    public record DailyClickStat(
            LocalDate date,
            long clicks
    ) {
    }

    public record ReferrerStat(
            String source,
            long count,
            double percentage
    ) {
    }

    public record CountryStat(
            String country,
            long count
    ) {
    }

    public record LinkStatsResponse(
            long totalClicks,
            long uniqueClicks,
            Instant lastClickedAt,
            List<ReferrerStat> referrers,
            List<DailyClickStat> dailyClicks,
            List<CountryStat> topCountries
    ) {
    }
}