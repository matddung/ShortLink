package com.studyjun.backend.link.reconciliation;

import com.studyjun.backend.link.LinkClickEvent;
import com.studyjun.backend.link.LinkClickEventRepository;
import com.studyjun.backend.link.ShortLink;
import com.studyjun.backend.link.ShortLinkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ClickCountReconciliationService.class)
class ClickCountReconciliationServiceTest {

    @Autowired
    private ClickCountReconciliationService clickCountReconciliationService;

    @Autowired
    private ShortLinkRepository shortLinkRepository;

    @Autowired
    private LinkClickEventRepository linkClickEventRepository;

    @Test
    void recalculateOne_overwritesStoredClicksFromRawEventCount() {
        ShortLink shortLink = shortLinkRepository.save(new ShortLink("https://example.com/a", "reco1", null, null));
        shortLinkRepository.overwriteTotalClicks(shortLink.getId(), 10L);

        linkClickEventRepository.save(new LinkClickEvent(
                UUID.randomUUID(),
                shortLink,
                Instant.parse("2026-03-27T00:00:00Z"),
                "req-1",
                "test",
                "KR",
                "https://search.example.com",
                "visitor-1"
        ));
        linkClickEventRepository.save(new LinkClickEvent(
                UUID.randomUUID(),
                shortLink,
                Instant.parse("2026-03-27T01:00:00Z"),
                "req-2",
                "test",
                "KR",
                "https://search.example.com",
                "visitor-2"
        ));

        ClickCountReconciliationService.SingleLinkRecalculationResult result =
                clickCountReconciliationService.recalculateOne(shortLink.getId());

        ShortLink reloaded = shortLinkRepository.findById(shortLink.getId()).orElseThrow();
        assertThat(result.updated()).isTrue();
        assertThat(result.storedTotalClicks()).isEqualTo(10L);
        assertThat(result.recalculatedTotalClicks()).isEqualTo(2L);
        assertThat(result.delta()).isEqualTo(-8L);
        assertThat(reloaded.getTotalClicks()).isEqualTo(2L);
    }

    @Test
    void findMismatches_and_recalculateAllMismatches_workForAllLinks() {
        ShortLink mismatched = shortLinkRepository.save(new ShortLink("https://example.com/b", "reco2", null, null));
        ShortLink alreadyConsistent = shortLinkRepository.save(new ShortLink("https://example.com/c", "reco3", null, null));

        shortLinkRepository.overwriteTotalClicks(mismatched.getId(), 99L);
        shortLinkRepository.overwriteTotalClicks(alreadyConsistent.getId(), 1L);

        linkClickEventRepository.save(new LinkClickEvent(
                UUID.randomUUID(),
                mismatched,
                Instant.parse("2026-03-27T02:00:00Z"),
                "req-3",
                "test",
                "US",
                "Direct",
                "visitor-3"
        ));

        linkClickEventRepository.save(new LinkClickEvent(
                UUID.randomUUID(),
                alreadyConsistent,
                Instant.parse("2026-03-27T03:00:00Z"),
                "req-4",
                "test",
                "US",
                "Direct",
                "visitor-4"
        ));

        List<ClickCountReconciliationService.ClickCountMismatch> mismatches = clickCountReconciliationService.findMismatches();

        assertThat(mismatches).hasSize(1);
        ClickCountReconciliationService.ClickCountMismatch mismatch = mismatches.get(0);
        assertThat(mismatch.shortLinkId()).isEqualTo(mismatched.getId());
        assertThat(mismatch.storedTotalClicks()).isEqualTo(99L);
        assertThat(mismatch.actualEventClicks()).isEqualTo(1L);
        assertThat(mismatch.delta()).isEqualTo(-98L);

        int updatedRows = clickCountReconciliationService.recalculateAllMismatches();

        ShortLink reloadedMismatched = shortLinkRepository.findById(mismatched.getId()).orElseThrow();
        ShortLink reloadedConsistent = shortLinkRepository.findById(alreadyConsistent.getId()).orElseThrow();
        assertThat(updatedRows).isEqualTo(1);
        assertThat(reloadedMismatched.getTotalClicks()).isEqualTo(1L);
        assertThat(reloadedConsistent.getTotalClicks()).isEqualTo(1L);
    }
}