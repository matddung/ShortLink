package com.studyjun.backend.analytics.reconciliation;

import com.studyjun.backend.analytics.clickevent.ClickAggregateUpdater;
import com.studyjun.backend.analytics.persistence.LinkClickEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class ClickCountReconciliationService {

    private final ClickAggregateUpdater clickAggregateUpdater;
    private final LinkClickEventRepository linkClickEventRepository;

    public ClickCountReconciliationService(ClickAggregateUpdater clickAggregateUpdater,
                                           LinkClickEventRepository linkClickEventRepository) {
        this.clickAggregateUpdater = clickAggregateUpdater;
        this.linkClickEventRepository = linkClickEventRepository;
    }

    @Transactional(readOnly = true)
    public List<ClickCountMismatch> findMismatches() {
        return linkClickEventRepository.findTotalClickMismatches().stream()
                .map(projection -> new ClickCountMismatch(
                        projection.getShortLinkId(),
                        projection.getStoredTotalClicks(),
                        projection.getActualEventClicks()
                ))
                .toList();
    }

    @Transactional
    public SingleLinkRecalculationResult recalculateOne(Long shortLinkId) {
        long storedTotalClicks = clickAggregateUpdater.getTotalClicks(shortLinkId);
        long recalculatedTotalClicks = linkClickEventRepository.countByShortLinkId(shortLinkId);

        if (storedTotalClicks != recalculatedTotalClicks) {
            clickAggregateUpdater.overwriteTotalClicks(shortLinkId, recalculatedTotalClicks);
            log.info("Recalculated total_clicks from link_click_events. shortLinkId={}, before={}, after={}",
                    shortLinkId, storedTotalClicks, recalculatedTotalClicks);
            return new SingleLinkRecalculationResult(shortLinkId, storedTotalClicks, recalculatedTotalClicks, true);
        }

        return new SingleLinkRecalculationResult(shortLinkId, storedTotalClicks, recalculatedTotalClicks, false);
    }

    @Transactional
    public int recalculateAllMismatches() {
        int updatedRows = clickAggregateUpdater.reconcileTotalClicksFromEvents();
        if (updatedRows > 0) {
            log.info("Reconciled total_clicks from link_click_events for mismatched rows. updatedRows={}", updatedRows);
        } else {
            log.debug("No total_clicks mismatches found during reconciliation run.");
        }
        return updatedRows;
    }

    public record ClickCountMismatch(
            Long shortLinkId,
            long storedTotalClicks,
            long actualEventClicks
    ) {
        public long delta() {
            return actualEventClicks - storedTotalClicks;
        }
    }

    public record SingleLinkRecalculationResult(
            Long shortLinkId,
            long storedTotalClicks,
            long recalculatedTotalClicks,
            boolean updated
    ) {
        public long delta() {
            return recalculatedTotalClicks - storedTotalClicks;
        }
    }
}
