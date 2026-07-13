package com.studyjun.backend.link.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortLinkTest {

    @Test
    void anonymousExpiredLinkIsNotRedirectable() {
        Instant now = Instant.parse("2026-07-12T00:00:00Z");
        ShortLink shortLink = new ShortLink(
                "https://example.com",
                "abc123",
                "owner-key",
                now.minusSeconds(1)
        );

        assertThat(shortLink.isAnonymousExpiredAt(now)).isTrue();
        assertThat(shortLink.redirectLookupStateAt(now)).isEqualTo(RedirectLookupState.EXPIRED);
        assertThat(shortLink.isRedirectableAt(now)).isFalse();
    }

    @Test
    void inactiveLinkIsNotRedirectableBeforeExpiryCheck() {
        Instant now = Instant.parse("2026-07-12T00:00:00Z");
        ShortLink shortLink = new ShortLink(
                "https://example.com",
                "abc123",
                "owner-key",
                now.minusSeconds(1)
        );

        shortLink.deactivate();

        assertThat(shortLink.redirectLookupStateAt(now)).isEqualTo(RedirectLookupState.INACTIVE);
        assertThat(shortLink.isRedirectableAt(now)).isFalse();
    }

    @Test
    void claimedUserLinkIsNotExpiredByOldAnonymousExpiry() {
        Instant now = Instant.parse("2026-07-12T00:00:00Z");
        ShortLink shortLink = new ShortLink(
                "https://example.com",
                "abc123",
                "owner-key",
                now.minusSeconds(1)
        );

        shortLink.claimToUser(10L);

        assertThat(shortLink.isAnonymous()).isFalse();
        assertThat(shortLink.isAnonymousExpiredAt(now)).isFalse();
        assertThat(shortLink.redirectLookupStateAt(now)).isEqualTo(RedirectLookupState.REDIRECTABLE);
    }

    @Test
    void clickCountDeltaMustBePositive() {
        ShortLink shortLink = new ShortLink("https://example.com", "abc123", null, null);

        shortLink.increaseClickCountBy(3);

        assertThat(shortLink.getTotalClicks()).isEqualTo(3);
        assertThatThrownBy(() -> shortLink.increaseClickCountBy(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
