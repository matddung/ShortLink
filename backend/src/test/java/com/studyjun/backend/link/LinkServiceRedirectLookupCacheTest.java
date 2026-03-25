package com.studyjun.backend.link;

import com.studyjun.backend.common.BusinessException;
import com.studyjun.backend.link.clickevent.ClickEventPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LinkServiceRedirectLookupCacheTest {

    @Mock
    private ShortLinkRepository shortLinkRepository;

    @Mock
    private LinkClickEventRepository linkClickEventRepository;

    @Mock
    private ClickEventPublisher clickEventPublisher;

    @Mock
    private RedirectLookupCacheRepository redirectLookupCacheRepository;

    @Mock
    private NegativeRedirectLookupCacheRepository negativeRedirectLookupCacheRepository;

    private RedirectLookupPolicy redirectLookupPolicy;
    private SimpleMeterRegistry meterRegistry;
    private LinkService linkService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        redirectLookupPolicy = new RedirectLookupPolicy();
        linkService = new LinkService(
                shortLinkRepository,
                linkClickEventRepository,
                clickEventPublisher,
                redirectLookupCacheRepository,
                negativeRedirectLookupCacheRepository,
                redirectLookupPolicy,
                meterRegistry,
                "http://localhost:8080",
                30
        );
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

        String originalUrl = linkService.resolveOriginalUrlSelectOnly("cache01");

        assertThat(originalUrl).isEqualTo("https://example.com/cached");
        verifyNoInteractions(shortLinkRepository);
        assertThat(meterRegistry.get("redirect.lookup.cache.hit.count").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("redirect.lookup.cache.miss.count").counter().count()).isZero();
        assertThat(meterRegistry.get("redirect.lookup.db.fallback.count").counter().count()).isZero();
    }

    @Test
    void resolveOriginalUrlSelectOnly_fallsBackToDbOnCacheMissAndWritesCache() {
        ShortLink shortLink = new ShortLink("https://example.com/db", "cache02", null, null);
        ReflectionTestUtils.setField(shortLink, "id", 202L);

        when(negativeRedirectLookupCacheRepository.findByShortCode("cache02")).thenReturn(Optional.empty());
        when(redirectLookupCacheRepository.findByShortCode("cache02")).thenReturn(Optional.empty());
        when(shortLinkRepository.findByShortCode("cache02")).thenReturn(Optional.of(shortLink));

        String originalUrl = linkService.resolveOriginalUrlSelectOnly("cache02");

        assertThat(originalUrl).isEqualTo("https://example.com/db");
        verify(shortLinkRepository).findByShortCode("cache02");
        verify(redirectLookupCacheRepository).save(eq("cache02"), any(RedirectLookupCacheRepository.RedirectLookupCacheEntry.class));
        assertThat(meterRegistry.get("redirect.lookup.cache.hit.count").counter().count()).isZero();
        assertThat(meterRegistry.get("redirect.lookup.cache.miss.count").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("redirect.lookup.db.fallback.count").counter().count()).isEqualTo(1.0);
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

        assertThatThrownBy(() -> linkService.resolveOriginalUrlSelectOnly("cache03"))
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

        assertThatThrownBy(() -> linkService.resolveOriginalUrlSelectOnly("cache04"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("링크를 찾을 수 없습니다.");

        verifyNoInteractions(shortLinkRepository);
        verifyNoInteractions(redirectLookupCacheRepository);
        assertThat(meterRegistry.get("redirect.lookup.cache.hit.count").counter().count()).isEqualTo(1.0);
    }

    @Test
    void resolveOriginalUrlSelectOnly_savesNegativeCacheWhenDbMisses() {
        when(negativeRedirectLookupCacheRepository.findByShortCode("cache05")).thenReturn(Optional.empty());
        when(redirectLookupCacheRepository.findByShortCode("cache05")).thenReturn(Optional.empty());
        when(shortLinkRepository.findByShortCode("cache05")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> linkService.resolveOriginalUrlSelectOnly("cache05"))
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

        assertThatThrownBy(() -> linkService.resolveOriginalUrlSelectOnly("cache06"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("링크를 찾을 수 없습니다.");

        verify(negativeRedirectLookupCacheRepository).save("cache06", NegativeRedirectReason.INACTIVE);
        verify(shortLinkRepository, never()).delete(any());
    }
}