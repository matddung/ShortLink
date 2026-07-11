package com.studyjun.backend.link.application.redirect;

public interface RedirectMetrics {

    void incrementRedirectCacheHit();

    void incrementRedirectCacheMiss();

    void incrementNegativeCacheHit();

    void incrementNegativeCacheMiss();

    void incrementRedirectDbFallback();
}
