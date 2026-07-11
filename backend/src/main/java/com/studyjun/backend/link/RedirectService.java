package com.studyjun.backend.link;

import com.studyjun.backend.link.application.redirect.RedirectClickEventRecorder;
import com.studyjun.backend.link.application.redirect.RedirectLookupService;
import com.studyjun.backend.link.application.redirect.RedirectRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RedirectService {

    private final RedirectLookupService redirectLookupService;
    private final RedirectClickEventRecorder redirectClickEventRecorder;

    public RedirectService(RedirectLookupService redirectLookupService,
                           RedirectClickEventRecorder redirectClickEventRecorder) {
        this.redirectLookupService = redirectLookupService;
        this.redirectClickEventRecorder = redirectClickEventRecorder;
    }

    @Transactional
    public String resolveOriginalUrl(String shortCode, String countryCode, String referrer, String visitorKey, String requestId, String source) {
        ResolvedRedirectTarget redirectTarget = redirectLookupService.resolveRedirectableTarget(shortCode);
        redirectClickEventRecorder.record(
                new RedirectRequest(shortCode, countryCode, referrer, visitorKey, requestId, source),
                redirectTarget
        );

        return redirectTarget.originalUrl();
    }

    @Transactional(readOnly = true)
    public String resolveOriginalUrlSelectOnly(String shortCode) {
        return redirectLookupService.resolveRedirectableTarget(shortCode).originalUrl();
    }
}