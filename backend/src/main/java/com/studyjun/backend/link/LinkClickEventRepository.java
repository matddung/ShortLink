package com.studyjun.backend.link;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LinkClickEventRepository extends JpaRepository<LinkClickEvent, Long> {
    boolean existsByEventId(UUID eventId);

    List<LinkClickEvent> findAllByShortLinkIdOrderByClickedAtDesc(Long shortLinkId);

    List<LinkClickEvent> findAllByShortLinkIdAndClickedAtGreaterThanEqualOrderByClickedAtAsc(Long shortLinkId, Instant clickedAt);

    long countByShortLinkId(Long shortLinkId);

    @Query(value = """
            select s.id as shortLinkId,
                   s.total_clicks as storedTotalClicks,
                   coalesce(e.event_count, 0) as actualEventClicks
              from short_links s
              left join (
                   select short_link_id, count(*) as event_count
                     from link_click_events
                    group by short_link_id
              ) e on e.short_link_id = s.id
             where s.total_clicks <> coalesce(e.event_count, 0)
             order by s.id
            """, nativeQuery = true)
    List<TotalClickMismatchProjection> findTotalClickMismatches();

    interface TotalClickMismatchProjection {
        Long getShortLinkId();

        Long getStoredTotalClicks();

        Long getActualEventClicks();
    }
}