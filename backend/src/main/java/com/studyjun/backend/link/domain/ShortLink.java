package com.studyjun.backend.link.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "short_links")
public class ShortLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2048)
    private String originalUrl;

    @Column(nullable = false, unique = true, length = 32)
    private String shortCode;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private long totalClicks;

    @Column(length = 64)
    private String ownerKey;

    @Column
    private Long ownerUserId;

    @Column
    private Instant anonymousExpiresAt;

    @Column(nullable = false)
    private boolean active;

    protected ShortLink() {
    }

    public ShortLink(String originalUrl, String shortCode, String ownerKey, Instant anonymousExpiresAt) {
        this.originalUrl = originalUrl;
        this.shortCode = shortCode;
        this.createdAt = Instant.now();
        this.totalClicks = 0;
        this.ownerKey = ownerKey;
        this.ownerUserId = null;
        this.anonymousExpiresAt = anonymousExpiresAt;
        this.active = true;
    }

    public void claimToUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        this.ownerUserId = userId;
        this.ownerKey = null;
        this.anonymousExpiresAt = null;
    }

    public void increaseClickCount() {
        increaseClickCountBy(1);
    }

    public void increaseClickCountBy(long delta) {
        if (delta <= 0) {
            throw new IllegalArgumentException("click count delta must be positive");
        }
        this.totalClicks += delta;
    }

    public void deactivate() {
        changeActive(false);
    }

    public void activate() {
        changeActive(true);
    }

    public void changeActive(boolean active) {
        this.active = active;
    }

    public boolean isAnonymous() {
        return ownerUserId == null;
    }

    public boolean isAnonymousExpiredAt(Instant now) {
        return isAnonymous()
                && anonymousExpiresAt != null
                && !anonymousExpiresAt.isAfter(now);
    }

    public RedirectLookupState redirectLookupStateAt(Instant now) {
        if (!active) {
            return RedirectLookupState.INACTIVE;
        }
        if (isAnonymousExpiredAt(now)) {
            return RedirectLookupState.EXPIRED;
        }
        return RedirectLookupState.REDIRECTABLE;
    }

    public boolean isRedirectableAt(Instant now) {
        return redirectLookupStateAt(now) == RedirectLookupState.REDIRECTABLE;
    }
}
