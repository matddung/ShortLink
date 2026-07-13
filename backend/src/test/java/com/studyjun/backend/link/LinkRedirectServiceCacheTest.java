package com.studyjun.backend.link;

import com.studyjun.backend.common.BusinessException;
import com.studyjun.backend.common.observability.ShortLinkMetrics;
import com.studyjun.backend.link.application.redirect.RedirectService;
import com.studyjun.backend.link.domain.NegativeRedirectReason;
import com.studyjun.backend.link.domain.RedirectLookupPolicy;
import com.studyjun.backend.link.application.redirect.CachedRedirectTarget;
import com.studyjun.backend.link.application.redirect.LinkRedirectService;
import com.studyjun.backend.link.application.redirect.NegativeRedirectCache;
import com.studyjun.backend.link.application.redirect.RedirectCacheInvalidator;
import com.studyjun.backend.link.application.redirect.RedirectClickEventRecorder;
import com.studyjun.backend.link.application.redirect.RedirectLookupService;
import com.studyjun.backend.link.application.redirect.RedirectTargetCache;
import com.studyjun.backend.link.application.redirect.RedirectTargetRepository;
import com.studyjun.backend.link.application.redirect.RedirectTargetSnapshot;
import com.studyjun.backend.link.application.redirect.ClickEventPublisher;
import com.studyjun.backend.link.application.redirect.RedirectClickEventMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
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
    private RedirectTargetRepository redirectTargetRepository;

    @Mock
    private ClickEventPublisher clickEventPublisher;

    @Mock
    private RedirectTargetCache redirectTargetCache;

    @Mock
    private NegativeRedirectCache negativeRedirectCache;

    private RedirectLookupPolicy redirectLookupPolicy;
    private SimpleMeterRegistry meterRegistry;
    private LinkRedirectService linkRedirectService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        redirectLookupPolicy = new RedirectLookupPolicy();

        RedirectLookupService redirectLookupService = new RedirectLookupService(
                redirectTargetRepository,
                redirectTargetCache,
                negativeRedirectCache,
                redirectLookupPolicy,
                new ShortLinkMetrics(meterRegistry),
                new RedirectCacheInvalidator(redirectTargetCache, negativeRedirectCache)
        );

        RedirectService redirectService = new RedirectService(
                redirectLookupService,
                new RedirectClickEventRecorder(clickEventPublisher, Clock.systemUTC())
        );
        linkRedirectService = new LinkRedirectService(redirectService);
    }

    @Test
    void resolveOriginalUrl_publishesClickEventAfterResolvingTarget() {
        RedirectTargetSnapshot target = new RedirectTargetSnapshot(101L, "https://example.com/redirect", null, true);

        when(negativeRedirectCache.findByShortCode("redir01")).thenReturn(Optional.empty());
        when(redirectTargetCache.findByShortCode("redir01")).thenReturn(Optional.empty());
        when(redirectTargetRepository.findByShortCode("redir01")).thenReturn(Optional.of(target));

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
    void resolveOriginalUrl_returnsRedirectUrlEvenWhenClickPublishFails() {
        RedirectTargetSnapshot target = new RedirectTargetSnapshot(101L, "https://example.com/redirect", null, true);

        when(negativeRedirectCache.findByShortCode("redir-fail")).thenReturn(Optional.empty());
        when(redirectTargetCache.findByShortCode("redir-fail")).thenReturn(Optional.empty());
        when(redirectTargetRepository.findByShortCode("redir-fail")).thenReturn(Optional.of(target));
        doThrow(new RuntimeException("publisher unavailable"))
                .when(clickEventPublisher)
                .publish(any(RedirectClickEventMessage.class));

        String originalUrl = linkRedirectService.resolveOriginalUrl(
                "redir-fail",
                "KR",
                "https://search.example.com",
                "visitor-01",
                "request-01",
                "test-source"
        );

        assertThat(originalUrl).isEqualTo("https://example.com/redirect");
        verify(clickEventPublisher).publish(any(RedirectClickEventMessage.class));
    }

    @Test
    void resolveOriginalUrlSelectOnly_usesCacheFirstWithoutDbFallback() {
        when(negativeRedirectCache.findByShortCode("cache01")).thenReturn(Optional.empty());
        when(redirectTargetCache.findByShortCode("cache01"))
                .thenReturn(Optional.of(new CachedRedirectTarget(
                        101L,
                        "https://example.com/cached",
                        null,
                        true
                )));

        String originalUrl = linkRedirectService.resolveOriginalUrlSelectOnly("cache01");

        assertThat(originalUrl).isEqualTo("https://example.com/cached");
        verifyNoInteractions(redirectTargetRepository);
        assertThat(meterRegistry.get("shortlink.redis.lookup.cache.hit.total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("shortlink.redis.lookup.cache.miss.total").counter().count()).isZero();
        assertThat(meterRegistry.get("shortlink.api.redirect.db_fallback.total").counter().count()).isZero();
        assertThat(meterRegistry.get("shortlink.redis.lookup.negative_cache.miss.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void resolveOriginalUrlSelectOnly_invalidatesExpiredPositiveCacheAndFallsBackToDb() {
        RedirectTargetSnapshot target = new RedirectTargetSnapshot(707L, "https://example.com/fresh", null, true);

        when(negativeRedirectCache.findByShortCode("cache-expired")).thenReturn(Optional.empty());
        when(redirectTargetCache.findByShortCode("cache-expired"))
                .thenReturn(Optional.of(new CachedRedirectTarget(
                        606L,
                        "https://example.com/stale",
                        Instant.now().minusSeconds(60),
                        true
                )));
        when(redirectTargetRepository.findByShortCode("cache-expired")).thenReturn(Optional.of(target));

        String originalUrl = linkRedirectService.resolveOriginalUrlSelectOnly("cache-expired");

        assertThat(originalUrl).isEqualTo("https://example.com/fresh");
        verify(redirectTargetCache).delete("cache-expired");
        verify(negativeRedirectCache, atLeastOnce()).delete("cache-expired");
        verify(redirectTargetRepository).findByShortCode("cache-expired");
        verify(redirectTargetCache).save(eq("cache-expired"), any(CachedRedirectTarget.class));
        assertThat(meterRegistry.get("shortlink.redis.lookup.cache.miss.total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("shortlink.api.redirect.db_fallback.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void resolveOriginalUrlSelectOnly_fallsBackToDbOnCacheMissAndWritesCache() {
        RedirectTargetSnapshot target = new RedirectTargetSnapshot(202L, "https://example.com/db", null, true);

        when(negativeRedirectCache.findByShortCode("cache02")).thenReturn(Optional.empty());
        when(redirectTargetCache.findByShortCode("cache02")).thenReturn(Optional.empty());
        when(redirectTargetRepository.findByShortCode("cache02")).thenReturn(Optional.of(target));

        String originalUrl = linkRedirectService.resolveOriginalUrlSelectOnly("cache02");

        assertThat(originalUrl).isEqualTo("https://example.com/db");
        verify(redirectTargetRepository).findByShortCode("cache02");
        verify(redirectTargetCache).save(eq("cache02"), any(CachedRedirectTarget.class));
        assertThat(meterRegistry.get("shortlink.redis.lookup.cache.hit.total").counter().count()).isZero();
        assertThat(meterRegistry.get("shortlink.redis.lookup.cache.miss.total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("shortlink.api.redirect.db_fallback.total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("shortlink.redis.lookup.negative_cache.miss.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void resolveOriginalUrlSelectOnly_deletesExpiredLinkAndThrowsNotFound() {
        RedirectTargetSnapshot expired = new RedirectTargetSnapshot(
                303L,
                "https://example.com/expired",
                Instant.now().minusSeconds(60),
                true
        );

        when(negativeRedirectCache.findByShortCode("cache03")).thenReturn(Optional.empty());
        when(redirectTargetCache.findByShortCode("cache03")).thenReturn(Optional.empty());
        when(redirectTargetRepository.findByShortCode("cache03")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> linkRedirectService.resolveOriginalUrlSelectOnly("cache03"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("LINK_NOT_FOUND"));

        verify(redirectTargetRepository).delete(expired);
        verify(redirectTargetCache).delete("cache03");
        verify(negativeRedirectCache).delete("cache03");
        verify(negativeRedirectCache).save("cache03", NegativeRedirectReason.EXPIRED);
        verify(redirectTargetCache, never()).save(eq("cache03"), any());
    }

    @Test
    void resolveOriginalUrlSelectOnly_throwsImmediatelyOnNegativeCacheHit() {
        when(negativeRedirectCache.findByShortCode("cache04"))
                .thenReturn(Optional.of(NegativeRedirectReason.NOT_FOUND));

        assertThatThrownBy(() -> linkRedirectService.resolveOriginalUrlSelectOnly("cache04"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("LINK_NOT_FOUND"));

        verifyNoInteractions(redirectTargetRepository);
        verifyNoInteractions(redirectTargetCache);
        assertThat(meterRegistry.get("shortlink.redis.lookup.negative_cache.hit.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void resolveOriginalUrlSelectOnly_savesNegativeCacheWhenDbMisses() {
        when(negativeRedirectCache.findByShortCode("cache05")).thenReturn(Optional.empty());
        when(redirectTargetCache.findByShortCode("cache05")).thenReturn(Optional.empty());
        when(redirectTargetRepository.findByShortCode("cache05")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> linkRedirectService.resolveOriginalUrlSelectOnly("cache05"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("LINK_NOT_FOUND"));

        verify(negativeRedirectCache).save("cache05", NegativeRedirectReason.NOT_FOUND);
        verify(redirectTargetCache, never()).save(eq("cache05"), any());
    }


    @Test
    void resolveOriginalUrlSelectOnly_savesInactiveNegativeCacheWhenLinkIsInactive() {
        RedirectTargetSnapshot inactive = new RedirectTargetSnapshot(606L, "https://example.com/inactive", null, false);

        when(negativeRedirectCache.findByShortCode("cache06")).thenReturn(Optional.empty());
        when(redirectTargetCache.findByShortCode("cache06")).thenReturn(Optional.empty());
        when(redirectTargetRepository.findByShortCode("cache06")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> linkRedirectService.resolveOriginalUrlSelectOnly("cache06"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("LINK_NOT_FOUND"));

        verify(negativeRedirectCache).save("cache06", NegativeRedirectReason.INACTIVE);
        verify(redirectTargetRepository, never()).delete(any());
    }
}
