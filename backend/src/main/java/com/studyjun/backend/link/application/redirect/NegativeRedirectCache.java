package com.studyjun.backend.link.application.redirect;

import com.studyjun.backend.link.domain.NegativeRedirectReason;

import java.util.Optional;

public interface NegativeRedirectCache {

    Optional<NegativeRedirectReason> findByShortCode(String shortCode);

    void save(String shortCode, NegativeRedirectReason reason);

    void delete(String shortCode);
}
