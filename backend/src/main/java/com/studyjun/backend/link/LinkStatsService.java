package com.studyjun.backend.link;

import com.studyjun.backend.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class LinkStatsService {

    private final ShortLinkRepository shortLinkRepository;
    private final LinkClickEventRepository linkClickEventRepository;

    public LinkStatsService(ShortLinkRepository shortLinkRepository,
                            LinkClickEventRepository linkClickEventRepository) {
        this.shortLinkRepository = shortLinkRepository;
        this.linkClickEventRepository = linkClickEventRepository;
    }

    @Transactional(readOnly = true)
    public LinkResponse.LinkStatsResponse getLinkStats(Long linkId, Long userId) {
        ShortLink link = shortLinkRepository.findByIdAndOwnerUserId(linkId, userId)
                .orElseThrow(() -> new BusinessException("LINK_NOT_FOUND", "링크를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        List<LinkClickEvent> allEvents = linkClickEventRepository.findAllByShortLinkIdOrderByClickedAtDesc(linkId);

        long uniqueClicks = allEvents.stream()
                .map(LinkClickEvent::getVisitorKey)
                .filter(Objects::nonNull)
                .filter(key -> !key.isBlank())
                .distinct()
                .count();

        Instant lastClickedAt = allEvents.isEmpty() ? null : allEvents.get(0).getClickedAt();

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate = today.minusDays(13);
        Instant startInstant = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);

        Map<LocalDate, Long> clickByDate = linkClickEventRepository
                .findAllByShortLinkIdAndClickedAtGreaterThanEqualOrderByClickedAtAsc(linkId, startInstant)
                .stream()
                .collect(Collectors.groupingBy(
                        event -> event.getClickedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                        Collectors.counting()
                ));

        List<LinkResponse.DailyClickStat> dailyClicks = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            LocalDate date = startDate.plusDays(i);
            dailyClicks.add(new LinkResponse.DailyClickStat(date, clickByDate.getOrDefault(date, 0L)));
        }

        List<LinkResponse.CountryStat> topCountries = allEvents.stream()
                .map(LinkClickEvent::getCountryCode)
                .map(country -> country == null || country.isBlank() ? "Unknown" : country)
                .collect(Collectors.groupingBy(country -> country, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> new LinkResponse.CountryStat(entry.getKey(), entry.getValue()))
                .toList();

        long totalEvents = allEvents.size();
        List<LinkResponse.ReferrerStat> topReferrers = allEvents.stream()
                .map(LinkClickEvent::getReferrer)
                .map(ref -> ref == null || ref.isBlank() ? "Direct" : ref)
                .collect(Collectors.groupingBy(ref -> ref, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    double percentage = totalEvents == 0 ? 0 : (entry.getValue() * 100.0) / totalEvents;
                    return new LinkResponse.ReferrerStat(entry.getKey(), entry.getValue(), Math.round(percentage * 10.0) / 10.0);
                })
                .toList();

        return new LinkResponse.LinkStatsResponse(
                link.getTotalClicks(),
                uniqueClicks,
                lastClickedAt,
                topReferrers,
                dailyClicks,
                topCountries
        );
    }
}