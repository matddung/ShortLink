package com.studyjun.backend.link;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {
    boolean existsByShortCode(String shortCode);

    Optional<ShortLink> findByShortCode(String shortCode);

    Optional<ShortLink> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    List<ShortLink> findAllByOwnerKeyAndOwnerUserIdIsNullOrderByCreatedAtDesc(String ownerKey);

    List<ShortLink> findAllByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

    List<ShortLink> findAllByOwnerUserIdIsNullAndAnonymousExpiresAtBefore(Instant threshold);

    long deleteByOwnerUserIdIsNullAndAnonymousExpiresAtBefore(Instant threshold);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ShortLink s set s.totalClicks = s.totalClicks + :delta where s.id = :shortLinkId")
    int incrementTotalClicks(@Param("shortLinkId") Long shortLinkId, @Param("delta") long delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ShortLink s set s.totalClicks = :totalClicks where s.id = :shortLinkId")
    int overwriteTotalClicks(@Param("shortLinkId") Long shortLinkId, @Param("totalClicks") long totalClicks);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update short_links s
               set total_clicks = coalesce((
                   select count(*)
                     from link_click_events e
                    where e.short_link_id = s.id
               ), 0)
             where s.total_clicks <> coalesce((
                   select count(*)
                     from link_click_events e
                    where e.short_link_id = s.id
               ), 0)
            """, nativeQuery = true)
    int reconcileTotalClicksFromEvents();
}