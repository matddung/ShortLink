package com.studyjun.backend.link;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(
        name = "redirect_click_outbox",
        indexes = {
                @Index(name = "ix_redirect_click_outbox_status_id", columnList = "status,id")
        }
)
public class RedirectClickOutbox {

    public enum Status {
        NEW,
        PROCESSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long shortLinkId;

    @Column(nullable = false)
    private Instant clickedAt;

    @Column(length = 8)
    private String countryCode;

    @Column(length = 255)
    private String referrer;

    @Column(length = 128)
    private String visitorKey;

    @Column(nullable = false, unique = true, length = 64)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant processedAt;

    protected RedirectClickOutbox() {
    }

    public RedirectClickOutbox(Long shortLinkId,
                               Instant clickedAt,
                               String countryCode,
                               String referrer,
                               String visitorKey,
                               String dedupeKey) {
        this.shortLinkId = shortLinkId;
        this.clickedAt = clickedAt;
        this.countryCode = countryCode;
        this.referrer = referrer;
        this.visitorKey = visitorKey;
        this.dedupeKey = dedupeKey;
        this.status = Status.NEW;
        this.createdAt = Instant.now();
    }

    public void markProcessed(Instant processedAt) {
        this.status = Status.PROCESSED;
        this.processedAt = processedAt;
    }
}