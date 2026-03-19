package com.studyjun.backend.link.clickevent;

import com.studyjun.backend.link.LinkClickEventRepository;
import com.studyjun.backend.link.ShortLink;
import com.studyjun.backend.link.ShortLinkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ClickEventAnalyticsServiceTest {

    @Autowired
    ClickEventAnalyticsService clickEventAnalyticsService;

    @Autowired
    ShortLinkRepository shortLinkRepository;

    @Autowired
    LinkClickEventRepository linkClickEventRepository;

    @Test
    void insertsRawEventBeforeUpdatingAggregateClicks() {
        ShortLink shortLink = shortLinkRepository.save(new ShortLink("https://example.com/target", "click01", null, null));
        RedirectClickEventMessage message = new RedirectClickEventMessage(
                UUID.randomUUID(),
                Instant.parse("2026-03-19T10:15:30Z").toString(),
                "req-analytics-1",
                "test-suite",
                shortLink.getId(),
                shortLink.getShortCode(),
                shortLink.getOriginalUrl(),
                "KR",
                "https://search.example.com/result",
                "visitor-analytics-1"
        );

        ClickEventAnalyticsService.ProcessingResult result = clickEventAnalyticsService.process(message);

        ShortLink reloaded = shortLinkRepository.findById(shortLink.getId()).orElseThrow();
        assertThat(result).isEqualTo(ClickEventAnalyticsService.ProcessingResult.INSERTED);
        assertThat(linkClickEventRepository.existsByEventId(message.eventId())).isTrue();
        assertThat(reloaded.getTotalClicks()).isEqualTo(1);
    }

    @Test
    void ignoresDuplicateEventIdsWithoutIncrementingAggregateClicksTwice() {
        ShortLink shortLink = shortLinkRepository.save(new ShortLink("https://example.com/target", "click02", null, null));
        UUID eventId = UUID.randomUUID();
        RedirectClickEventMessage message = new RedirectClickEventMessage(
                eventId,
                Instant.parse("2026-03-19T10:20:30Z").toString(),
                "req-analytics-2",
                "test-suite",
                shortLink.getId(),
                shortLink.getShortCode(),
                shortLink.getOriginalUrl(),
                "US",
                "Direct",
                "visitor-analytics-2"
        );

        clickEventAnalyticsService.process(message);
        ClickEventAnalyticsService.ProcessingResult duplicateResult = clickEventAnalyticsService.process(message);

        ShortLink reloaded = shortLinkRepository.findById(shortLink.getId()).orElseThrow();
        assertThat(duplicateResult).isEqualTo(ClickEventAnalyticsService.ProcessingResult.DUPLICATE);
        assertThat(linkClickEventRepository.count()).isEqualTo(1);
        assertThat(reloaded.getTotalClicks()).isEqualTo(1);
    }
}