package com.studyjun.backend.link;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LinkClickEventRepository extends JpaRepository<LinkClickEvent, Long> {
    boolean existsByEventId(UUID eventId);

    List<LinkClickEvent> findAllByShortLinkIdOrderByClickedAtDesc(Long shortLinkId);

    List<LinkClickEvent> findAllByShortLinkIdAndClickedAtGreaterThanEqualOrderByClickedAtAsc(Long shortLinkId, Instant clickedAt);
}