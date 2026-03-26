package com.studyjun.backend.link.application.redirect;

import com.studyjun.backend.link.RedirectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkRedirectService {

    private final RedirectService redirectService;

    public LinkRedirectService(RedirectService redirectService) {
        this.redirectService = redirectService;
    }

    @Transactional
    public String resolveOriginalUrl(String shortCode, String countryCode, String referrer, String visitorKey, String requestId, String source) {
        return redirectService.resolveOriginalUrl(shortCode, countryCode, referrer, visitorKey, requestId, source);
    }

    @Transactional(readOnly = true)
    public String resolveOriginalUrlSelectOnly(String shortCode) {
        return redirectService.resolveOriginalUrlSelectOnly(shortCode);
    }
}