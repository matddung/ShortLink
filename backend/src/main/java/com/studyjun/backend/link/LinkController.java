package com.studyjun.backend.link;

import com.studyjun.backend.common.ApiResponse;
import com.studyjun.backend.common.BusinessException;
import com.studyjun.backend.user.User;
import com.studyjun.backend.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class LinkController {

    private static final String ANONYMOUS_OWNER_COOKIE = "anonymous_owner";
    private static final Logger log = LoggerFactory.getLogger(LinkController.class);

    private final LinkService linkService;
    private final boolean secureCookie;
    private final String sameSite;
    private final UserRepository userRepository;
    private final long anonymousExpirationDays;
    private final String analyticsSource;
    private final List<String> countryHeaderCandidates;
    private final Map<String, String> languageCountryFallbacks;

    public LinkController(LinkService linkService,
                          UserRepository userRepository,
                          @Value("${app.auth.secure-cookie:true}") boolean secureCookie,
                          @Value("${app.auth.same-site:Lax}") String sameSite,
                          @Value("${app.anonymous.expiration-days:30}") long anonymousExpirationDays,
                          @Value("${spring.application.name:shortlink-backend}") String analyticsSource,
                          @Value("${app.geo.country-headers:CF-IPCountry,CloudFront-Viewer-Country,X-AppEngine-Country,X-Country-Code,X-Vercel-IP-Country,Fastly-Geo-Country-Code,X-Geo-Country}") String countryHeaders,
                          @Value("${app.geo.language-country-fallbacks:ko:KR,ja:JP,en:US}") String languageCountryFallbacks) {
        this.linkService = linkService;
        this.userRepository = userRepository;
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
        this.anonymousExpirationDays = anonymousExpirationDays;
        this.analyticsSource = analyticsSource;
        this.countryHeaderCandidates = List.of(countryHeaders.split(","))
                .stream()
                .map(String::trim)
                .filter(header -> !header.isBlank())
                .toList();
        this.languageCountryFallbacks = List.of(languageCountryFallbacks.split(","))
                .stream()
                .map(String::trim)
                .filter(entry -> entry.contains(":"))
                .map(entry -> entry.split(":", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(
                        parts -> parts[0].trim().toLowerCase(),
                        parts -> {
                            String normalizedCountry = normalizeCountryCode(parts[1]);
                            return normalizedCountry == null ? "" : normalizedCountry;
                        },
                        (left, right) -> right
                ));
    }

    @PostMapping("/api/links/anonymous")
    public ResponseEntity<ApiResponse<LinkResponse.ShortLinkResponse>> createAnonymous(
            @Valid @RequestBody LinkRequest.CreateAnonymousRequest request,
            @CookieValue(name = ANONYMOUS_OWNER_COOKIE, required = false) String ownerKey
    ) {
        String effectiveOwnerKey = normalizeOwnerKey(ownerKey);
        LinkResponse.ShortLinkResponse response = linkService.createAnonymous(request.originalUrl(), effectiveOwnerKey);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, createOwnerCookie(effectiveOwnerKey))
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
        String countryCode = resolveCountryCode(request);
        String referrer = resolveReferrer(request);
        String visitorKey = buildVisitorKey(request);
        String requestId = resolveRequestId(request);

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

    private String normalizeOwnerKey(String ownerKey) {
        if (ownerKey == null || ownerKey.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return ownerKey;
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestIdHeader = request.getHeader("X-Request-Id");
        if (isUsableRequestId(requestIdHeader)) {
            return requestIdHeader;
        }

        String requestId = request.getRequestId();
        if (isUsableRequestId(requestId)) {
            return requestId;
        }

        return UUID.randomUUID().toString();
    }

    private boolean isUsableRequestId(String requestId) {
        return requestId != null
                && !requestId.isBlank()
                && !"0".equals(requestId.trim());
    }

    private String createOwnerCookie(String ownerKey) {
        return ResponseCookie.from(ANONYMOUS_OWNER_COOKIE, ownerKey)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ofDays(anonymousExpirationDays))
                .build()
                .toString();
    }

    private String resolveCountryCode(HttpServletRequest request) {
        for (String header : countryHeaderCandidates) {
            String country = normalizeCountryCode(request.getHeader(header));
            if (country != null) {
                return country;
            }
        }

        String acceptLanguage = request.getHeader("Accept-Language");
        if (acceptLanguage != null && !acceptLanguage.isBlank()) {
            String firstTag = acceptLanguage.split(",")[0].trim();
            Locale locale = Locale.forLanguageTag(firstTag);
            String fromTag = normalizeCountryCode(locale.getCountry());
            if (fromTag != null) {
                return fromTag;
            }

            if (firstTag.contains("-")) {
                String[] parts = firstTag.split("-");
                if (parts.length > 1) {
                    String fromRaw = normalizeCountryCode(parts[parts.length - 1]);
                    if (fromRaw != null) {
                        return fromRaw;
                    }
                }
            }

            String lang = locale.getLanguage();
            if (lang != null && !lang.isBlank()) {
                String mappedCountry = normalizeCountryCode(languageCountryFallbacks.get(lang.toLowerCase()));
                if (mappedCountry != null) {
                    return mappedCountry;
                }
            }
        }

        String localeCountry = normalizeCountryCode(request.getLocale() != null ? request.getLocale().getCountry() : null);
        if (localeCountry != null) {
            return localeCountry;
        }

        if (log.isDebugEnabled()) {
            String geoHeaderDump = countryHeaderCandidates.stream()
                    .map(header -> header + "=" + request.getHeader(header))
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("none");
            log.debug("Country detection fallback to Unknown. geoHeaders=[{}], Accept-Language='{}', requestLocale='{}', remoteAddr='{}', xForwardedFor='{}', languageCountryFallbacks={}",
                    geoHeaderDump,
                    request.getHeader("Accept-Language"),
                    request.getLocale(),
                    request.getRemoteAddr(),
                    request.getHeader("X-Forwarded-For"),
                    languageCountryFallbacks
            );
        }

        return "Unknown";
    }

    private String normalizeCountryCode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.length() == 2 && normalized.chars().allMatch(Character::isLetter)) {
            return normalized;
        }
        return null;
    }

    private String resolveReferrer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return "Direct";
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getHost() != null && !uri.getHost().isBlank()) {
                return uri.getHost();
            }
        } catch (IllegalArgumentException ignored) {
        }
        return "Direct";
    }

    private String buildVisitorKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = (forwardedFor != null && !forwardedFor.isBlank())
                ? forwardedFor.split(",")[0].trim()
                : request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent") == null ? "" : request.getHeader("User-Agent");
        String raw = ip + "|" + userAgent;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes());
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(raw.hashCode());
        }
    }
}