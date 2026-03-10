package com.studyjun.backend.link;

import com.studyjun.backend.common.ApiResponse;
import com.studyjun.backend.common.BusinessException;
import com.studyjun.backend.user.User;
import com.studyjun.backend.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
public class LinkController {

    private static final String ANONYMOUS_OWNER_COOKIE = "anonymous_owner";

    private final LinkService linkService;
    private final boolean secureCookie;
    private final String sameSite;
    private final UserRepository userRepository;
    private final long anonymousExpirationDays;

    public LinkController(LinkService linkService,
                          UserRepository userRepository,
                          @Value("${app.auth.secure-cookie:true}") boolean secureCookie,
                          @Value("${app.auth.same-site:Lax}") String sameSite,
                          @Value("${app.anonymous.expiration-days:30}") long anonymousExpirationDays) {
        this.linkService = linkService;
        this.userRepository = userRepository;
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
        this.anonymousExpirationDays = anonymousExpirationDays;
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
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        return ApiResponse.ok(linkService.createForUser(request.originalUrl(), request.customCode(), user.getId()));
    }

    @GetMapping("/api/links")
    public ApiResponse<List<LinkResponse.ShortLinkResponse>> getMyLinks(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        return ApiResponse.ok(linkService.getUserLinks(user.getId()));
    }

    @GetMapping("/s/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String originalUrl = linkService.resolveOriginalUrl(shortCode);
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, URI.create(originalUrl).toString())
                .build();
    }
    private String normalizeOwnerKey(String ownerKey) {
        if (ownerKey == null || ownerKey.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return ownerKey;
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
}