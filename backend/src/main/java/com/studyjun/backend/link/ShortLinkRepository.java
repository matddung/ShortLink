package com.studyjun.backend.link;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {
    boolean existsByShortCode(String shortCode);

    Optional<ShortLink> findByShortCode(String shortCode);

    List<ShortLink> findAllByOwnerKeyAndOwnerUserIdIsNullOrderByCreatedAtDesc(String ownerKey);

    List<ShortLink> findAllByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

    long deleteByOwnerUserIdIsNullAndAnonymousExpiresAtBefore(Instant threshold);
}