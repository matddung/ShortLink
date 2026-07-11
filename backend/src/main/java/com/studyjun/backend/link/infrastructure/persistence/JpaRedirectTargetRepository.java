package com.studyjun.backend.link.infrastructure.persistence;

import com.studyjun.backend.link.ShortLinkRepository;
import com.studyjun.backend.link.application.redirect.RedirectTargetRepository;
import com.studyjun.backend.link.application.redirect.RedirectTargetSnapshot;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JpaRedirectTargetRepository implements RedirectTargetRepository {

    private final ShortLinkRepository shortLinkRepository;

    public JpaRedirectTargetRepository(ShortLinkRepository shortLinkRepository) {
        this.shortLinkRepository = shortLinkRepository;
    }

    @Override
    public Optional<RedirectTargetSnapshot> findByShortCode(String shortCode) {
        return shortLinkRepository.findByShortCode(shortCode)
                .map(shortLink -> new RedirectTargetSnapshot(
                        shortLink.getId(),
                        shortLink.getOriginalUrl(),
                        shortLink.getAnonymousExpiresAt(),
                        shortLink.isActive()
                ));
    }

    @Override
    public void delete(RedirectTargetSnapshot target) {
        shortLinkRepository.deleteById(target.shortLinkId());
    }
}
