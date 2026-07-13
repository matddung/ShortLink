package com.studyjun.backend.link.infrastructure.persistence;

import com.studyjun.backend.analytics.clickevent.ClickAggregateUpdater;
import com.studyjun.backend.link.infrastructure.persistence.ShortLinkRepository;
import org.springframework.stereotype.Component;

@Component
public class JpaClickAggregateUpdater implements ClickAggregateUpdater {

    private final ShortLinkRepository shortLinkRepository;

    public JpaClickAggregateUpdater(ShortLinkRepository shortLinkRepository) {
        this.shortLinkRepository = shortLinkRepository;
    }

    @Override
    public int incrementTotalClicks(Long shortLinkId, long delta) {
        return shortLinkRepository.incrementTotalClicks(shortLinkId, delta);
    }

    @Override
    public long getTotalClicks(Long shortLinkId) {
        return shortLinkRepository.findById(shortLinkId)
                .orElseThrow(() -> new IllegalArgumentException("Short link not found. id=" + shortLinkId))
                .getTotalClicks();
    }

    @Override
    public int overwriteTotalClicks(Long shortLinkId, long totalClicks) {
        return shortLinkRepository.overwriteTotalClicks(shortLinkId, totalClicks);
    }

    @Override
    public int reconcileTotalClicksFromEvents() {
        return shortLinkRepository.reconcileTotalClicksFromEvents();
    }
}
