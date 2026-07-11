package com.studyjun.backend.link.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record LinkStatsResult(
        long totalClicks,
        long uniqueClicks,
        Instant lastClickedAt,
        List<ReferrerStat> referrers,
        List<DailyClickStat> dailyClicks,
        List<CountryStat> topCountries
) {
    public record DailyClickStat(LocalDate date, long clicks) {
    }

    public record ReferrerStat(String source, long count, double percentage) {
    }

    public record CountryStat(String country, long count) {
    }
}
