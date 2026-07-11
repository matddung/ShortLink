package com.studyjun.backend.analytics.stat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record LinkClickStats(
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
