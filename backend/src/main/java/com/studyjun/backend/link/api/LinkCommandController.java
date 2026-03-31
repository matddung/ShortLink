package com.studyjun.backend.link.api;

import com.studyjun.backend.common.ApiResponse;
import com.studyjun.backend.link.LinkRequest;
import com.studyjun.backend.link.LinkResponse;
import com.studyjun.backend.link.application.command.LinkCommandService;
import com.studyjun.backend.link.support.AnonymousOwnerCookieManager;
import com.studyjun.backend.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/links")
public class LinkCommandController {

    private static final String ANONYMOUS_OWNER_COOKIE = "anonymous_owner";

    private final LinkCommandService linkCommandService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final AnonymousOwnerCookieManager anonymousOwnerCookieManager;

    public LinkCommandController(LinkCommandService linkCommandService,
                                 AuthenticatedUserResolver authenticatedUserResolver,
                                 AnonymousOwnerCookieManager anonymousOwnerCookieManager) {
        this.linkCommandService = linkCommandService;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.anonymousOwnerCookieManager = anonymousOwnerCookieManager;
    }

    @PostMapping("/anonymous")
    public ResponseEntity<ApiResponse<LinkResponse.ShortLinkResponse>> createAnonymous(
            @Valid @RequestBody LinkRequest.CreateAnonymousRequest request,
            @CookieValue(name = ANONYMOUS_OWNER_COOKIE, required = false) String ownerKey
    ) {
        String effectiveOwnerKey = anonymousOwnerCookieManager.normalizeOwnerKey(ownerKey);
        LinkResponse.ShortLinkResponse response = linkCommandService.createAnonymous(request.originalUrl(), effectiveOwnerKey);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, anonymousOwnerCookieManager.createOwnerCookie(effectiveOwnerKey))
                .body(ApiResponse.ok(response));
    }

    @PostMapping
    public ApiResponse<LinkResponse.ShortLinkResponse> createMyLink(
            @Valid @RequestBody LinkRequest.CreateLinkRequest request,
            Authentication authentication
    ) {
        User user = authenticatedUserResolver.resolve(authentication);
        return ApiResponse.ok(linkCommandService.createForUser(request.originalUrl(), request.customCode(), user.getId()));
    }
}