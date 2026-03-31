package com.studyjun.backend.link.api;

import com.studyjun.backend.link.ShortLinkMetrics;
import com.studyjun.backend.link.application.redirect.LinkRedirectService;
import com.studyjun.backend.link.support.GeoResolver;
import com.studyjun.backend.link.support.ReferrerResolver;
import com.studyjun.backend.link.support.RequestIdResolver;
import com.studyjun.backend.link.support.VisitorFingerprintService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class RedirectController {

    private final LinkRedirectService linkRedirectService;
    private final String analyticsSource;
    private final RequestIdResolver requestIdResolver;
    private final GeoResolver geoResolver;
    private final ReferrerResolver referrerResolver;
    private final VisitorFingerprintService visitorFingerprintService;
    private final ShortLinkMetrics shortLinkMetrics;

    public RedirectController(LinkRedirectService linkRedirectService,
                              @Value("${spring.application.name:shortlink-backend}") String analyticsSource,
                              RequestIdResolver requestIdResolver,
                              GeoResolver geoResolver,
                              ReferrerResolver referrerResolver,
                              VisitorFingerprintService visitorFingerprintService,
                              ShortLinkMetrics shortLinkMetrics) {
        this.linkRedirectService = linkRedirectService;
        this.analyticsSource = analyticsSource;
        this.requestIdResolver = requestIdResolver;
        this.geoResolver = geoResolver;
        this.referrerResolver = referrerResolver;
        this.visitorFingerprintService = visitorFingerprintService;
        this.shortLinkMetrics = shortLinkMetrics;
    }

    @GetMapping("/s/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        long startNanos = System.nanoTime();
        try {
            String countryCode = geoResolver.resolveCountryCode(request);
            String referrer = referrerResolver.resolve(request);
            String visitorKey = visitorFingerprintService.buildVisitorKey(request);
            String requestId = requestIdResolver.resolve(request);

            String originalUrl = linkRedirectService.resolveOriginalUrl(shortCode, countryCode, referrer, visitorKey, requestId, analyticsSource);
            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, URI.create(originalUrl).toString())
                    .build();
        } finally {
            shortLinkMetrics.redirectLatencyTimer().record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
        }
    }

    @GetMapping("/s-select/{shortCode}")
    public ResponseEntity<Void> redirectSelectOnly(@PathVariable String shortCode) {
        String originalUrl = linkRedirectService.resolveOriginalUrlSelectOnly(shortCode);
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, URI.create(originalUrl).toString())
                .build();
    }
}