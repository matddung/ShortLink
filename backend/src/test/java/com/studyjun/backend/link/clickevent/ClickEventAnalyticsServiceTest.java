package com.studyjun.backend.link.clickevent;

import com.studyjun.backend.link.LinkClickEventRepository;
import com.studyjun.backend.link.ShortLink;
import com.studyjun.backend.link.ShortLinkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class ClickEventAnalyticsServiceTest {

    @Autowired
    ClickEventAnalyticsService clickEventAnalyticsService;

    @Autowired
    ShortLinkRepository shortLinkRepository;

    @Autowired
    LinkClickEventRepository linkClickEventRepository;

    @Autowired
    ClickCountFlushWorker clickCountFlushWorker;

    @MockBean
    ClickCountBufferService clickCountBufferService;

    @Test
    void insertsRawEventAndFlushesBufferedAggregateIncrement() {
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
        when(clickCountBufferService.increment(shortLink.getId())).thenReturn(1L);
        when(clickCountBufferService.findBufferedKeys()).thenReturn(Set.of("analytics:click-count:" + shortLink.getId()));
        when(clickCountBufferService.tryAcquireFlushLock(anyString(), any(Duration.class))).thenReturn(true);
        when(clickCountBufferService.extractShortLinkId("analytics:click-count:" + shortLink.getId())).thenReturn(shortLink.getId());
        when(clickCountBufferService.consumeBufferedCount("analytics:click-count:" + shortLink.getId())).thenReturn(1L);

        ClickEventAnalyticsService.ProcessingResult result = clickEventAnalyticsService.process(message);
        clickCountFlushWorker.flush();

        ShortLink reloaded = shortLinkRepository.findById(shortLink.getId()).orElseThrow();
        assertThat(result).isEqualTo(ClickEventAnalyticsService.ProcessingResult.INSERTED);
        assertThat(linkClickEventRepository.existsByEventId(message.eventId())).isTrue();
        assertThat(reloaded.getTotalClicks()).isEqualTo(1);
        verify(clickCountBufferService).increment(shortLink.getId());
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
        when(clickCountBufferService.increment(anyLong())).thenReturn(1L);
        when(clickCountBufferService.tryAcquireFlushLock(anyString(), any(Duration.class))).thenReturn(true);
        when(clickCountBufferService.findBufferedKeys()).thenReturn(Set.of("analytics:click-count:" + shortLink.getId()));
        when(clickCountBufferService.extractShortLinkId("analytics:click-count:" + shortLink.getId())).thenReturn(shortLink.getId());
        when(clickCountBufferService.consumeBufferedCount("analytics:click-count:" + shortLink.getId())).thenReturn(1L);

        clickEventAnalyticsService.process(message);
        ClickEventAnalyticsService.ProcessingResult duplicateResult = clickEventAnalyticsService.process(message);
        clickCountFlushWorker.flush();

        ShortLink reloaded = shortLinkRepository.findById(shortLink.getId()).orElseThrow();
        assertThat(duplicateResult).isEqualTo(ClickEventAnalyticsService.ProcessingResult.DUPLICATE);
        assertThat(linkClickEventRepository.count()).isEqualTo(1);
        assertThat(reloaded.getTotalClicks()).isEqualTo(1);
        verify(clickCountBufferService, times(1)).increment(shortLink.getId());
    }
}