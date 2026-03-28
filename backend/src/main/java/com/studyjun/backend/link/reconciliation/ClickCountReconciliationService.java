package com.studyjun.backend.link.reconciliation;

import com.studyjun.backend.link.LinkClickEventRepository;
import com.studyjun.backend.link.ShortLink;
import com.studyjun.backend.link.ShortLinkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class ClickCountReconciliationService {

    private final ShortLinkRepository shortLinkRepository;
    private final LinkClickEventRepository linkClickEventRepository;

    public ClickCountReconciliationService(ShortLinkRepository shortLinkRepository,
                                           LinkClickEventRepository linkClickEventRepository) {
        this.shortLinkRepository = shortLinkRepository;
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
        ShortLink shortLink = shortLinkRepository.findById(shortLinkId)
                .orElseThrow(() -> new IllegalArgumentException("Short link not found. id=" + shortLinkId));

        long storedTotalClicks = shortLink.getTotalClicks();
        long recalculatedTotalClicks = linkClickEventRepository.countByShortLinkId(shortLinkId);

        if (storedTotalClicks != recalculatedTotalClicks) {
            shortLinkRepository.overwriteTotalClicks(shortLinkId, recalculatedTotalClicks);
            log.info("Recalculated total_clicks from link_click_events. shortLinkId={}, before={}, after={}",
                    shortLinkId, storedTotalClicks, recalculatedTotalClicks);
            return new SingleLinkRecalculationResult(shortLinkId, storedTotalClicks, recalculatedTotalClicks, true);
        }

        return new SingleLinkRecalculationResult(shortLinkId, storedTotalClicks, recalculatedTotalClicks, false);
    }

    @Transactional
    public int recalculateAllMismatches() {
        int updatedRows = shortLinkRepository.reconcileTotalClicksFromEvents();
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