package com.studyjun.backend.link;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "link_click_events")
public class LinkClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "short_link_id", nullable = false)
    private ShortLink shortLink;

    @Column(nullable = false)
    private Instant clickedAt;

    @Column(length = 8)
    private String countryCode;

    @Column(length = 255)
    private String referrer;

    @Column(length = 128)
    private String visitorKey;

    protected LinkClickEvent() {
    }

    public LinkClickEvent(ShortLink shortLink, Instant clickedAt, String countryCode, String referrer, String visitorKey) {
        this.shortLink = shortLink;
        this.clickedAt = clickedAt;
        this.countryCode = countryCode;
        this.referrer = referrer;
        this.visitorKey = visitorKey;
    }
}