package com.studyjun.backend.analytics.clickevent;

import com.studyjun.backend.analytics.infrastructure.optional.redis.ClickCountBufferService;
import com.studyjun.backend.analytics.persistence.LinkClickEvent;
import com.studyjun.backend.analytics.persistence.LinkClickEventRepository;
import com.studyjun.backend.link.application.redirect.RedirectClickEventMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
public class ClickEventAnalyticsService {

    private final LinkClickEventRepository linkClickEventRepository;
    private final ClickCountBufferService clickCountBufferService;
    private final ClickAggregateUpdater clickAggregateUpdater;

    public ClickEventAnalyticsService(LinkClickEventRepository linkClickEventRepository,
                                      ClickCountBufferService clickCountBufferService,
                                      ClickAggregateUpdater clickAggregateUpdater) {
        this.linkClickEventRepository = linkClickEventRepository;
        this.clickCountBufferService = clickCountBufferService;
        this.clickAggregateUpdater = clickAggregateUpdater;
    }

    @Transactional
    public ProcessingResult process(RedirectClickEventMessage message) {
        if (linkClickEventRepository.existsByEventId(message.eventId())) {
            log.info("Skipping duplicate click event. eventId={}, shortCode={}, requestId={}",
                    message.eventId(), message.shortCode(), message.requestId());
            return ProcessingResult.DUPLICATE;
        }

        linkClickEventRepository.saveAndFlush(new LinkClickEvent(
                message.eventId(),
                message.shortLinkId(),
                Instant.parse(message.clickedAt()),
                message.requestId(),
                message.source(),
                message.countryCode(),
                message.referrer(),
                message.visitorKey()
        ));

        try {
            long bufferedCount = clickCountBufferService.increment(message.shortLinkId());
            log.info("Persisted click event and buffered aggregate click increment. eventId={}, shortCode={}, requestId={}, bufferedCount={}",
                    message.eventId(), message.shortCode(), message.requestId(), bufferedCount);
        } catch (RuntimeException ex) {
            clickAggregateUpdater.incrementTotalClicks(message.shortLinkId(), 1);
            log.info("Persisted click event and directly incremented aggregate click count because Redis buffering is unavailable. eventId={}, shortCode={}, requestId={}",
                    message.eventId(), message.shortCode(), message.requestId());
        }
        return ProcessingResult.INSERTED;
    }

    public enum ProcessingResult {
        INSERTED,
        DUPLICATE
    }
}
