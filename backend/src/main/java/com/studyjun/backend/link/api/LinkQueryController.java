package com.studyjun.backend.link.api;

import com.studyjun.backend.common.ApiResponse;
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
    private final LinkResponseMapper linkResponseMapper;

    public LinkQueryController(LinkQueryService linkQueryService,
                               AuthenticatedUserResolver authenticatedUserResolver,
                               LinkResponseMapper linkResponseMapper) {
        this.linkQueryService = linkQueryService;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.linkResponseMapper = linkResponseMapper;
    }

    @GetMapping("/anonymous")
    public ApiResponse<List<LinkResponse.ShortLinkResponse>> getAnonymousLinks(
            @CookieValue(name = ANONYMOUS_OWNER_COOKIE, required = false) String ownerKey
    ) {
        if (ownerKey == null || ownerKey.isBlank()) {
            return ApiResponse.ok(List.of());
        }
        return ApiResponse.ok(linkResponseMapper.toShortLinkResponses(linkQueryService.getAnonymousLinks(ownerKey)));
    }

    @GetMapping
    public ApiResponse<List<LinkResponse.ShortLinkResponse>> getMyLinks(Authentication authentication) {
        User user = authenticatedUserResolver.resolve(authentication);
        return ApiResponse.ok(linkResponseMapper.toShortLinkResponses(linkQueryService.getUserLinks(user.getId())));
    }

    @GetMapping("/{id}/stats")
    public ApiResponse<LinkResponse.LinkStatsResponse> getLinkStats(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = authenticatedUserResolver.resolve(authentication);
        return ApiResponse.ok(linkResponseMapper.toResponse(linkQueryService.getLinkStats(id, user.getId())));
    }
}
