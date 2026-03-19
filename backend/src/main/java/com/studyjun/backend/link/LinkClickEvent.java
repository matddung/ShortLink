package com.studyjun.backend.link;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "link_click_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_link_click_events_event_id", columnNames = "event_id")
        }
)
public class LinkClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false, unique = true)
    private UUID eventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "short_link_id", nullable = false)
    private ShortLink shortLink;

    @Column(nullable = false)
    private Instant clickedAt;

    @Column(nullable = false, length = 128)
    private String requestId;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(length = 8)
    private String countryCode;

    @Column(length = 255)
    private String referrer;

    @Column(length = 128)
    private String visitorKey;

    protected LinkClickEvent() {
    }

    public LinkClickEvent(UUID eventId,
                          ShortLink shortLink,
                          Instant clickedAt,
                          String requestId,
                          String source,
                          String countryCode,
                          String referrer,
                          String visitorKey) {
        this.eventId = eventId;
        this.shortLink = shortLink;
        this.clickedAt = clickedAt;
        this.requestId = requestId;
        this.source = source;
        this.countryCode = countryCode;
        this.referrer = referrer;
        this.visitorKey = visitorKey;
    }
}