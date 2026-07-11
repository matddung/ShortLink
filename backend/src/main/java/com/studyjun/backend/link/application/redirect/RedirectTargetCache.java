package com.studyjun.backend.link.application.redirect;

import java.util.Optional;

public interface RedirectTargetCache {

    Optional<CachedRedirectTarget> findByShortCode(String shortCode);

    void save(String shortCode, CachedRedirectTarget target);

    void delete(String shortCode);
}
