package com.studyjun.backend.link.application.redirect;

import java.util.Optional;

public interface RedirectTargetRepository {

    Optional<RedirectTargetSnapshot> findByShortCode(String shortCode);

    void delete(RedirectTargetSnapshot target);
}
