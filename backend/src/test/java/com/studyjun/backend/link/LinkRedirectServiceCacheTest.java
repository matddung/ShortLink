package com.studyjun.backend.link;

import com.studyjun.backend.common.BusinessException;
import com.studyjun.backend.link.application.redirect.LinkRedirectService;
import com.studyjun.backend.link.clickevent.ClickEventPublisher;
import com.studyjun.backend.link.clickevent.RedirectClickEventMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LinkRedirectServiceCacheTest {

    @Mock
    private ShortLinkRepository shortLinkRepository;

    @Mock
    private ClickEventPublisher clickEventPublisher;

    @Mock
    private RedirectLookupCacheRepository redirectLookupCacheRepository;

    @Mock
    private NegativeRedirectLookupCacheRepository negativeRedirectLookupCacheRepository;

    private RedirectLookupPolicy redirectLookupPolicy;
    private SimpleMeterRegistry meterRegistry;
    private LinkRedirectService linkRedirectService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        redirectLookupPolicy = new RedirectLookupPolicy();

        RedirectService redirectService = new RedirectService(
                shortLinkRepository,
                clickEventPublisher,
                redirectLookupCacheRepository,
                negativeRedirectLookupCacheRepository,
                redirectLookupPolicy,
                new ShortLinkMetrics(meterRegistry)
        );
        linkRedirectService = new LinkRedirectService(redirectService);
    }

    @Test
    void resolveOriginalUrl_publishesClickEventAfterResolvingTarget() {
        ShortLink shortLink = new ShortLink("https://example.com/redirect", "redir01", null, null);
        ReflectionTestUtils.setField(shortLink, "id", 101L);

        when(negativeRedirectLookupCacheRepository.findByShortCode("redir01")).thenReturn(Optional.empty());
        when(redirectLookupCacheRepository.findByShortCode("redir01")).thenReturn(Optional.empty());
        when(shortLinkRepository.findByShortCode("redir01")).thenReturn(Optional.of(shortLink));

        String originalUrl = linkRedirectService.resolveOriginalUrl(
                "redir01",
                "KR",
                "https://search.example.com",
                "visitor-01",
                "request-01",
                "test-source"
        );

        assertThat(originalUrl).isEqualTo("https://example.com/redirect");

        ArgumentCaptor<RedirectClickEventMessage> eventCaptor = ArgumentCaptor.forClass(RedirectClickEventMessage.class);
        verify(clickEventPublisher).publish(eventCaptor.capture());

        RedirectClickEventMessage event = eventCaptor.getValue();
        assertThat(event.eventId()).isEqualTo(UUID.nameUUIDFromBytes("101:request-01".getBytes(StandardCharsets.UTF_8)));
        assertThat(event.clickedAt()).isNotBlank();
        assertThat(event.requestId()).isEqualTo("request-01");
        assertThat(event.source()).isEqualTo("test-source");
        assertThat(event.shortLinkId()).isEqualTo(101L);
        assertThat(event.shortCode()).isEqualTo("redir01");
        assertThat(event.originalUrl()).isEqualTo("https://example.com/redirect");
        assertThat(event.countryCode()).isEqualTo("KR");
        assertThat(event.referrer()).isEqualTo("https://search.example.com");
        assertThat(event.visitorKey()).isEqualTo("visitor-01");
    }

    @Test
    void resolveOriginalUrlSelectOnly_usesCacheFirstWithoutDbFallback() {
        when(negativeRedirectLookupCacheRepository.findByShortCode("cache01")).thenReturn(Optional.empty());
        when(redirectLookupCacheRepository.findByShortCode("cache01"))
                .thenReturn(Optional.of(new RedirectLookupCacheRepository.RedirectLookupCacheEntry(
                        101L,
                        "https://example.com/cached",
                        null,
                        true
                )));

        String originalUrl = linkRedirectService.resolveOriginalUrlSelectOnly("cache01");

        assertThat(originalUrl).isEqualTo("https://example.com/cached");
        verifyNoInteractions(shortLinkRepository);
        assertThat(meterRegistry.get("shortlink.redis.lookup.cache.hit.total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("shortlink.redis.lookup.cache.miss.total").counter().count()).isZero();
        assertThat(meterRegistry.get("shortlink.api.redirect.db_fallback.total").counter().count()).isZero();
        assertThat(meterRegistry.get("shortlink.redis.lookup.negative_cache.miss.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void resolveOriginalUrlSelectOnly_invalidatesExpiredPositiveCacheAndFallsBackToDb() {
        ShortLink shortLink = new ShortLink("https://example.com/fresh", "cache-expired", null, null);
        ReflectionTestUtils.setField(shortLink, "id", 707L);

        when(negativeRedirectLookupCacheRepository.findByShortCode("cache-expired")).thenReturn(Optional.empty());
        when(redirectLookupCacheRepository.findByShortCode("cache-expired"))
                .thenReturn(Optional.of(new RedirectLookupCacheRepository.RedirectLookupCacheEntry(
                        606L,
                        "https://example.com/stale",
                        Instant.now().minusSeconds(60),
                        true
                )));
        when(shortLinkRepository.findByShortCode("cache-expired")).thenReturn(Optional.of(shortLink));

        String originalUrl = linkRedirectService.resolveOriginalUrlSelectOnly("cache-expired");

        assertThat(originalUrl).isEqualTo("https://example.com/fresh");
        verify(redirectLookupCacheRepository).delete("cache-expired");
        verify(negativeRedirectLookupCacheRepository, atLeastOnce()).delete("cache-expired");
        verify(shortLinkRepository).findByShortCode("cache-expired");
        verify(redirectLookupCacheRepository).save(eq("cache-expired"), any(RedirectLookupCacheRepository.RedirectLookupCacheEntry.class));
        assertThat(meterRegistry.get("shortlink.redis.lookup.cache.miss.total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("shortlink.api.redirect.db_fallback.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void resolveOriginalUrlSelectOnly_fallsBackToDbOnCacheMissAndWritesCache() {
        ShortLink shortLink = new ShortLink("https://example.com/db", "cache02", null, null);
        ReflectionTestUtils.setField(shortLink, "id", 202L);

        when(negativeRedirectLookupCacheRepository.findByShortCode("cache02")).thenReturn(Optional.empty());
        when(redirectLookupCacheRepository.findByShortCode("cache02")).thenReturn(Optional.empty());
        when(shortLinkRepository.findByShortCode("cache02")).thenReturn(Optional.of(shortLink));

        String originalUrl = linkRedirectService.resolveOriginalUrlSelectOnly("cache02");

        assertThat(originalUrl).isEqualTo("https://example.com/db");
        verify(shortLinkRepository).findByShortCode("cache02");
        verify(redirectLookupCacheRepository).save(eq("cache02"), any(RedirectLookupCacheRepository.RedirectLookupCacheEntry.class));
        assertThat(meterRegistry.get("shortlink.redis.lookup.cache.hit.total").counter().count()).isZero();
        assertThat(meterRegistry.get("shortlink.redis.lookup.cache.miss.total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("shortlink.api.redirect.db_fallback.total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("shortlink.redis.lookup.negative_cache.miss.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void resolveOriginalUrlSelectOnly_deletesExpiredLinkAndThrowsNotFound() {
        ShortLink expired = new ShortLink(
                "https://example.com/expired",
                "cache03",
                "owner",
                Instant.now().minusSeconds(60)
        );
        ReflectionTestUtils.setField(expired, "id", 303L);

        when(negativeRedirectLookupCacheRepository.findByShortCode("cache03")).thenReturn(Optional.empty());
        when(redirectLookupCacheRepository.findByShortCode("cache03")).thenReturn(Optional.empty());
        when(shortLinkRepository.findByShortCode("cache03")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> linkRedirectService.resolveOriginalUrlSelectOnly("cache03"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("링크를 찾을 수 없습니다.");

        verify(shortLinkRepository).delete(expired);
        verify(redirectLookupCacheRepository).delete("cache03");
        verify(negativeRedirectLookupCacheRepository).delete("cache03");
        verify(negativeRedirectLookupCacheRepository).save("cache03", NegativeRedirectReason.EXPIRED);
        verify(redirectLookupCacheRepository, never()).save(eq("cache03"), any());
    }

    @Test
    void resolveOriginalUrlSelectOnly_throwsImmediatelyOnNegativeCacheHit() {
        when(negativeRedirectLookupCacheRepository.findByShortCode("cache04"))
                .thenReturn(Optional.of(NegativeRedirectReason.NOT_FOUND));

        assertThatThrownBy(() -> linkRedirectService.resolveOriginalUrlSelectOnly("cache04"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("링크를 찾을 수 없습니다.");

        verifyNoInteractions(shortLinkRepository);
        verifyNoInteractions(redirectLookupCacheRepository);
        assertThat(meterRegistry.get("shortlink.redis.lookup.negative_cache.hit.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void resolveOriginalUrlSelectOnly_savesNegativeCacheWhenDbMisses() {
        when(negativeRedirectLookupCacheRepository.findByShortCode("cache05")).thenReturn(Optional.empty());
        when(redirectLookupCacheRepository.findByShortCode("cache05")).thenReturn(Optional.empty());
        when(shortLinkRepository.findByShortCode("cache05")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> linkRedirectService.resolveOriginalUrlSelectOnly("cache05"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("링크를 찾을 수 없습니다.");

        verify(negativeRedirectLookupCacheRepository).save("cache05", NegativeRedirectReason.NOT_FOUND);
        verify(redirectLookupCacheRepository, never()).save(eq("cache05"), any());
    }


    @Test
    void resolveOriginalUrlSelectOnly_savesInactiveNegativeCacheWhenLinkIsInactive() {
        ShortLink inactive = new ShortLink("https://example.com/inactive", "cache06", null, null);
        ReflectionTestUtils.setField(inactive, "id", 606L);
        inactive.deactivate();

        when(negativeRedirectLookupCacheRepository.findByShortCode("cache06")).thenReturn(Optional.empty());
        when(redirectLookupCacheRepository.findByShortCode("cache06")).thenReturn(Optional.empty());
        when(shortLinkRepository.findByShortCode("cache06")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> linkRedirectService.resolveOriginalUrlSelectOnly("cache06"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("링크를 찾을 수 없습니다.");

        verify(negativeRedirectLookupCacheRepository).save("cache06", NegativeRedirectReason.INACTIVE);
        verify(shortLinkRepository, never()).delete(any());
    }
}
