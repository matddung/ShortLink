package com.studyjun.backend.link;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface LinkClickEventRepository extends JpaRepository<LinkClickEvent, Long> {
    List<LinkClickEvent> findAllByShortLinkIdOrderByClickedAtDesc(Long shortLinkId);

    List<LinkClickEvent> findAllByShortLinkIdAndClickedAtGreaterThanEqualOrderByClickedAtAsc(Long shortLinkId, Instant clickedAt);
}