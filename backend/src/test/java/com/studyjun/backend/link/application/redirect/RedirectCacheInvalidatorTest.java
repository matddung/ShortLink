package com.studyjun.backend.link.application.redirect;

import com.studyjun.backend.link.domain.ShortLink;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RedirectCacheInvalidatorTest {

    private final RedirectTargetCache redirectTargetCache = mock(RedirectTargetCache.class);
    private final NegativeRedirectCache negativeRedirectCache = mock(NegativeRedirectCache.class);
    private final RedirectCacheInvalidator redirectCacheInvalidator =
            new RedirectCacheInvalidator(redirectTargetCache, negativeRedirectCache);

    @Test
    void invalidateDeletesPositiveAndNegativeCacheEntries() {
        redirectCacheInvalidator.invalidate("abc123");

        verify(redirectTargetCache).delete("abc123");
        verify(negativeRedirectCache).delete("abc123");
    }

    @Test
    void invalidateAllDeletesEveryShortLinkCacheEntry() {
        ShortLink first = new ShortLink("https://example.com/one", "one123", null, null);
        ShortLink second = new ShortLink("https://example.com/two", "two123", null, null);

        redirectCacheInvalidator.invalidateAll(List.of(first, second));

        verify(redirectTargetCache).delete("one123");
        verify(redirectTargetCache).delete("two123");
        verify(negativeRedirectCache).delete("one123");
        verify(negativeRedirectCache).delete("two123");
    }
}
