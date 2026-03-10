package com.studyjun.backend.link;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AnonymousLinkCleanupScheduler {

    private final LinkService linkService;

    public AnonymousLinkCleanupScheduler(LinkService linkService) {
        this.linkService = linkService;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredAnonymousLinks() {
        linkService.purgeExpiredAnonymousLinks();
    }
}