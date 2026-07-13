package com.studyjun.backend.link.application.query;

import com.studyjun.backend.analytics.stat.LinkClickStats;
import com.studyjun.backend.analytics.stat.LinkClickStatsQueryService;
import com.studyjun.backend.common.BusinessException;
import com.studyjun.backend.link.application.LinkStatsResult;
import com.studyjun.backend.link.domain.ShortLink;
import com.studyjun.backend.link.infrastructure.persistence.ShortLinkRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkStatsService {

    private final ShortLinkRepository shortLinkRepository;
    private final LinkClickStatsQueryService linkClickStatsQueryService;

    public LinkStatsService(ShortLinkRepository shortLinkRepository,
                            LinkClickStatsQueryService linkClickStatsQueryService) {
        this.shortLinkRepository = shortLinkRepository;
        this.linkClickStatsQueryService = linkClickStatsQueryService;
    }

    @Transactional(readOnly = true)
    public LinkStatsResult getLinkStats(Long linkId, Long userId) {
        ShortLink link = shortLinkRepository.findByIdAndOwnerUserId(linkId, userId)
                .orElseThrow(() -> new BusinessException("LINK_NOT_FOUND", "Link not found.", HttpStatus.NOT_FOUND));

        LinkClickStats clickStats = linkClickStatsQueryService.getStats(linkId);

        return new LinkStatsResult(
                link.getTotalClicks(),
                clickStats.uniqueClicks(),
                clickStats.lastClickedAt(),
                clickStats.referrers().stream()
                        .map(referrer -> new LinkStatsResult.ReferrerStat(
                                referrer.source(),
                                referrer.count(),
                                referrer.percentage()
                        ))
                        .toList(),
                clickStats.dailyClicks().stream()
                        .map(dailyClick -> new LinkStatsResult.DailyClickStat(
                                dailyClick.date(),
                                dailyClick.clicks()
                        ))
                        .toList(),
                clickStats.topCountries().stream()
                        .map(country -> new LinkStatsResult.CountryStat(
                                country.country(),
                                country.count()
                        ))
                        .toList()
        );
    }
}
