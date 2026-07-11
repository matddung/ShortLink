package com.studyjun.backend.link.application.redirect;

import com.studyjun.backend.link.ResolvedRedirectTarget;
import com.studyjun.backend.analytics.clickevent.ClickEventPublisher;
import com.studyjun.backend.analytics.clickevent.RedirectClickEventMessage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class RedirectClickEventRecorder {

    private final ClickEventPublisher clickEventPublisher;
    private final Clock clock;

    public RedirectClickEventRecorder(ClickEventPublisher clickEventPublisher, Clock clock) {
        this.clickEventPublisher = clickEventPublisher;
        this.clock = clock;
    }

    public void record(RedirectRequest request, ResolvedRedirectTarget target) {
        clickEventPublisher.publish(new RedirectClickEventMessage(
                buildEventId(target.shortLinkId(), request.requestId()),
                DateTimeFormatter.ISO_INSTANT.format(clock.instant()),
                request.requestId(),
                request.source(),
                target.shortLinkId(),
                request.shortCode(),
                target.originalUrl(),
                request.countryCode(),
                request.referrer(),
                request.visitorKey()
        ));
    }

    private UUID buildEventId(Long shortLinkId, String requestId) {
        return UUID.nameUUIDFromBytes((shortLinkId + ":" + requestId).getBytes(StandardCharsets.UTF_8));
    }
}
