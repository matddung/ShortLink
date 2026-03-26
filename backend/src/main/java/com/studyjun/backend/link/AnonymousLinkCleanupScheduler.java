package com.studyjun.backend.link;

import com.studyjun.backend.link.application.command.LinkCommandService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AnonymousLinkCleanupScheduler {

    private final LinkCommandService linkCommandService;

    public AnonymousLinkCleanupScheduler(LinkCommandService linkCommandService) {
        this.linkCommandService = linkCommandService;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredAnonymousLinks() {
        linkCommandService.purgeExpiredAnonymousLinks();
    }
}