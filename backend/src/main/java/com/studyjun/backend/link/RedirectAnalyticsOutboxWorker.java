package com.studyjun.backend.link;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RedirectAnalyticsOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(RedirectAnalyticsOutboxWorker.class);

    private final RedirectClickOutboxRepository outboxRepository;
    private final ShortLinkRepository shortLinkRepository;
    private final LinkClickEventRepository linkClickEventRepository;
    private final boolean enabled;

    public RedirectAnalyticsOutboxWorker(RedirectClickOutboxRepository outboxRepository,
                                         ShortLinkRepository shortLinkRepository,
                                         LinkClickEventRepository linkClickEventRepository,
                                         @Value("${app.analytics.outbox.enabled:true}") boolean enabled) {
        this.outboxRepository = outboxRepository;
        this.shortLinkRepository = shortLinkRepository;
        this.linkClickEventRepository = linkClickEventRepository;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.analytics.outbox.fixed-delay-ms:500}")
    @Transactional
    public void processBatch() {
        if (!enabled) {
            return;
        }

        List<RedirectClickOutbox> batch = outboxRepository.findTop100ByStatusOrderByIdAsc(RedirectClickOutbox.Status.NEW);
        if (batch.isEmpty()) {
            return;
        }

        Set<Long> shortLinkIds = batch.stream()
                .map(RedirectClickOutbox::getShortLinkId)
                .collect(Collectors.toSet());
        Map<Long, ShortLink> shortLinkById = shortLinkRepository.findAllById(shortLinkIds)
                .stream()
                .collect(Collectors.toMap(ShortLink::getId, link -> link));

        List<LinkClickEvent> eventsToSave = new ArrayList<>();
        Map<Long, Long> clickDeltaByShortLinkId = new HashMap<>();
        Instant processedAt = Instant.now();

        for (RedirectClickOutbox outbox : batch) {
            try {
                ShortLink shortLink = shortLinkById.get(outbox.getShortLinkId());
                if (shortLink == null) {
                    outbox.markProcessed(processedAt);
                    continue;
                }

                eventsToSave.add(new LinkClickEvent(
                        shortLink,
                        outbox.getClickedAt(),
                        outbox.getCountryCode(),
                        outbox.getReferrer(),
                        outbox.getVisitorKey()
                ));

                clickDeltaByShortLinkId.merge(outbox.getShortLinkId(), 1L, Long::sum);
                outbox.markProcessed(processedAt);
            } catch (Exception e) {
                log.warn("Failed to process outbox id={}, shortLinkId={}", outbox.getId(), outbox.getShortLinkId(), e);
            }
        }

        if (!eventsToSave.isEmpty()) {
            linkClickEventRepository.saveAll(eventsToSave);
        }

        for (Map.Entry<Long, Long> entry : clickDeltaByShortLinkId.entrySet()) {
            shortLinkRepository.incrementTotalClicks(entry.getKey(), entry.getValue());
        }
    }
}