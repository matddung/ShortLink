package com.studyjun.backend.link;

import com.studyjun.backend.common.ApiResponse;
import com.studyjun.backend.common.BusinessException;
import com.studyjun.backend.link.support.*;
import com.studyjun.backend.user.User;
import com.studyjun.backend.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@Slf4j
@RestController
public class LinkController {

    private static final String ANONYMOUS_OWNER_COOKIE = "anonymous_owner";

    private final LinkService linkService;
    private final UserRepository userRepository;
    private final String analyticsSource;
    private final RequestIdResolver requestIdResolver;
    private final GeoResolver geoResolver;
    private final ReferrerResolver referrerResolver;
    private final VisitorFingerprintService visitorFingerprintService;
    private final AnonymousOwnerCookieManager anonymousOwnerCookieManager;

    public LinkController(LinkService linkService,
                          UserRepository userRepository,
                          @Value("${spring.application.name:shortlink-backend}") String analyticsSource,
                          RequestIdResolver requestIdResolver,
                          GeoResolver geoResolver,
                          ReferrerResolver referrerResolver,
                          VisitorFingerprintService visitorFingerprintService,
                          AnonymousOwnerCookieManager anonymousOwnerCookieManager) {
        this.linkService = linkService;
        this.userRepository = userRepository;
        this.analyticsSource = analyticsSource;
        this.requestIdResolver = requestIdResolver;
        this.geoResolver = geoResolver;
        this.referrerResolver = referrerResolver;
        this.visitorFingerprintService = visitorFingerprintService;
        this.anonymousOwnerCookieManager = anonymousOwnerCookieManager;
    }

    @PostMapping("/api/links/anonymous")
    public ResponseEntity<ApiResponse<LinkResponse.ShortLinkResponse>> createAnonymous(
            @Valid @RequestBody LinkRequest.CreateAnonymousRequest request,
            @CookieValue(name = ANONYMOUS_OWNER_COOKIE, required = false) String ownerKey
    ) {
        String effectiveOwnerKey = anonymousOwnerCookieManager.normalizeOwnerKey(ownerKey);
        LinkResponse.ShortLinkResponse response = linkService.createAnonymous(request.originalUrl(), effectiveOwnerKey);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, anonymousOwnerCookieManager.createOwnerCookie(effectiveOwnerKey))
                .body(ApiResponse.ok(response));
    }

    @GetMapping("/api/links/anonymous")
    public ApiResponse<List<LinkResponse.ShortLinkResponse>> getAnonymousLinks(
            @CookieValue(name = ANONYMOUS_OWNER_COOKIE, required = false) String ownerKey
    ) {
        if (ownerKey == null || ownerKey.isBlank()) {
            return ApiResponse.ok(List.of());
        }
        return ApiResponse.ok(linkService.getAnonymousLinks(ownerKey));
    }

    @PostMapping("/api/links")
    public ApiResponse<LinkResponse.ShortLinkResponse> createMyLink(
            @Valid @RequestBody LinkRequest.CreateLinkRequest request,
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);
        return ApiResponse.ok(linkService.createForUser(request.originalUrl(), request.customCode(), user.getId()));
    }

    @GetMapping("/api/links")
    public ApiResponse<List<LinkResponse.ShortLinkResponse>> getMyLinks(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ApiResponse.ok(linkService.getUserLinks(user.getId()));
    }

    @GetMapping("/api/links/{id}/stats")
    public ApiResponse<LinkResponse.LinkStatsResponse> getLinkStats(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);
        return ApiResponse.ok(linkService.getLinkStats(id, user.getId()));
    }

    @GetMapping("/s/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        String countryCode = geoResolver.resolveCountryCode(request);
        String referrer = referrerResolver.resolve(request);
        String visitorKey = visitorFingerprintService.buildVisitorKey(request);
        String requestId = requestIdResolver.resolve(request);

        String originalUrl = linkService.resolveOriginalUrl(shortCode, countryCode, referrer, visitorKey, requestId, analyticsSource);
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, URI.create(originalUrl).toString())
                .build();
    }

    @GetMapping("/s-select/{shortCode}")
    public ResponseEntity<Void> redirectSelectOnly(@PathVariable String shortCode) {
        String originalUrl = linkService.resolveOriginalUrlSelectOnly(shortCode);
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, URI.create(originalUrl).toString())
                .build();
    }

    private User getAuthenticatedUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }
}