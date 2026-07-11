package com.studyjun.backend.link.infrastructure.event;

import com.studyjun.backend.analytics.clickevent.ClickEventAnalyticsService;
import com.studyjun.backend.analytics.clickevent.ClickEventPublisher;
import com.studyjun.backend.analytics.clickevent.RedirectClickEventMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DirectClickEventPublisher implements ClickEventPublisher {

    private final ClickEventAnalyticsService clickEventAnalyticsService;

    public DirectClickEventPublisher(ClickEventAnalyticsService clickEventAnalyticsService) {
        this.clickEventAnalyticsService = clickEventAnalyticsService;
    }

    @Override
    public void publish(RedirectClickEventMessage message) {
        try {
            clickEventAnalyticsService.process(message);
        } catch (Exception ex) {
            log.error(
                    "Failed to record click event through direct fallback. eventId={}, shortCode={}, requestId={}",
                    message.eventId(),
                    message.shortCode(),
                    message.requestId(),
                    ex
            );
        }
    }
}
