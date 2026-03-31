package com.studyjun.backend.link.api;

import com.studyjun.backend.common.ApiResponse;
import com.studyjun.backend.link.LinkResponse;
import com.studyjun.backend.link.application.query.LinkQueryService;
import com.studyjun.backend.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/links")
public class LinkQueryController {

    private static final String ANONYMOUS_OWNER_COOKIE = "anonymous_owner";

    private final LinkQueryService linkQueryService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public LinkQueryController(LinkQueryService linkQueryService,
                               AuthenticatedUserResolver authenticatedUserResolver) {
        this.linkQueryService = linkQueryService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @GetMapping("/anonymous")
    public ApiResponse<List<LinkResponse.ShortLinkResponse>> getAnonymousLinks(
            @CookieValue(name = ANONYMOUS_OWNER_COOKIE, required = false) String ownerKey
    ) {
        if (ownerKey == null || ownerKey.isBlank()) {
            return ApiResponse.ok(List.of());
        }
        return ApiResponse.ok(linkQueryService.getAnonymousLinks(ownerKey));
    }

    @GetMapping
    public ApiResponse<List<LinkResponse.ShortLinkResponse>> getMyLinks(Authentication authentication) {
        User user = authenticatedUserResolver.resolve(authentication);
        return ApiResponse.ok(linkQueryService.getUserLinks(user.getId()));
    }

    @GetMapping("/{id}/stats")
    public ApiResponse<LinkResponse.LinkStatsResponse> getLinkStats(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = authenticatedUserResolver.resolve(authentication);
        return ApiResponse.ok(linkQueryService.getLinkStats(id, user.getId()));
    }
}