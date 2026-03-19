package com.studyjun.backend.link.clickevent;

import com.studyjun.backend.link.LinkClickEvent;
import com.studyjun.backend.link.LinkClickEventRepository;
import com.studyjun.backend.link.ShortLink;
import com.studyjun.backend.link.ShortLinkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Slf4j
@Service
public class ClickEventAnalyticsService {

    private final ShortLinkRepository shortLinkRepository;
    private final LinkClickEventRepository linkClickEventRepository;

    public ClickEventAnalyticsService(ShortLinkRepository shortLinkRepository,
                                      LinkClickEventRepository linkClickEventRepository) {
        this.shortLinkRepository = shortLinkRepository;
        this.linkClickEventRepository = linkClickEventRepository;
    }

    @Transactional
    public ProcessingResult process(RedirectClickEventMessage message) {
        if (linkClickEventRepository.existsByEventId(message.eventId())) {
            log.info("Skipping duplicate click event. eventId={}, shortCode={}, requestId={}",
                    message.eventId(), message.shortCode(), message.requestId());
            return ProcessingResult.DUPLICATE;
        }

        ShortLink shortLink = shortLinkRepository.findById(message.shortLinkId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Short link not found for click event: " + message.shortLinkId()));

        if (!shortLink.getShortCode().equals(message.shortCode())) {
            throw new IllegalArgumentException("Short code mismatch for click event " + message.eventId());
        }

        linkClickEventRepository.saveAndFlush(new LinkClickEvent(
                message.eventId(),
                shortLink,
                Instant.parse(message.clickedAt()),
                message.requestId(),
                message.source(),
                message.countryCode(),
                message.referrer(),
                message.visitorKey()
        ));

        shortLink.increaseClickCount();
        log.info("Persisted click event. eventId={}, shortCode={}, requestId={}, totalClicks={}",
                message.eventId(), message.shortCode(), message.requestId(), shortLink.getTotalClicks());
        return ProcessingResult.INSERTED;
    }

    public enum ProcessingResult {
        INSERTED,
        DUPLICATE
    }
}