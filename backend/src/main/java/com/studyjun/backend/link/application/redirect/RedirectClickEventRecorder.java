package com.studyjun.backend.link.application.redirect;

import com.studyjun.backend.link.domain.ResolvedRedirectTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Component
public class RedirectClickEventRecorder {

    private final ClickEventPublisher clickEventPublisher;
    private final Clock clock;

    public RedirectClickEventRecorder(ClickEventPublisher clickEventPublisher, Clock clock) {
        this.clickEventPublisher = clickEventPublisher;
        this.clock = clock;
    }

    public void record(RedirectRequest request, ResolvedRedirectTarget target) {
        try {
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
        } catch (RuntimeException ex) {
            log.error(
                    "Redirect succeeded but click event publishing failed. shortLinkId={}, shortCode={}, requestId={}",
                    target.shortLinkId(),
                    request.shortCode(),
                    request.requestId(),
                    ex
            );
        }
    }

    private UUID buildEventId(Long shortLinkId, String requestId) {
        return UUID.nameUUIDFromBytes((shortLinkId + ":" + requestId).getBytes(StandardCharsets.UTF_8));
    }
}
