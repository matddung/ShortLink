package com.studyjun.backend.auth;

import com.studyjun.backend.common.ApiResponse;
import com.studyjun.backend.link.LinkService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
    private static final String ANONYMOUS_OWNER_COOKIE_NAME = "anonymous_owner";

    private final AuthService authService;
    private final boolean secureCookie;
    private final String sameSite;
    private final long refreshTokenExpirationMs;
    private final LinkService linkService;

    public AuthController(AuthService authService,
                          LinkService linkService,
                          @Value("${app.auth.secure-cookie:true}") boolean secureCookie,
                          @Value("${app.auth.same-site:Lax}") String sameSite,
                          @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        this.authService = authService;
        this.linkService = linkService;
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse.UserResponse>> signup(
            @Valid @RequestBody AuthRequest.SignupRequest request,
            @CookieValue(name = ANONYMOUS_OWNER_COOKIE_NAME, required = false) String anonymousOwner
    ) {
        AuthResponse.UserResponse user = authService.signup(request);
        int moved = linkService.claimAnonymousLinks(anonymousOwner, user.id());

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (moved > 0) {
            builder.header(HttpHeaders.SET_COOKIE, clearAnonymousOwnerCookie());
        }

        return builder.body(ApiResponse.ok(user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse.TokenResponse>> login(
            @Valid @RequestBody AuthRequest.LoginRequest request,
            @CookieValue(name = ANONYMOUS_OWNER_COOKIE_NAME, required = false) String anonymousOwner
    ) {
        AuthResponse.TokenResponse tokenResponse = authService.login(request);
        AuthResponse.UserResponse user = authService.getCurrentUser(request.email());
        int moved = linkService.claimAnonymousLinks(anonymousOwner, user.id());

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, createRefreshTokenCookie(tokenResponse.refreshToken()));

        if (moved > 0) {
            builder.header(HttpHeaders.SET_COOKIE, clearAnonymousOwnerCookie());
        }

        return builder.body(ApiResponse.ok(withoutRefreshToken(tokenResponse)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse.TokenResponse>> refresh(@CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {
        AuthResponse.TokenResponse tokenResponse = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, createRefreshTokenCookie(tokenResponse.refreshToken()))
                .body(ApiResponse.ok(withoutRefreshToken(tokenResponse)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(@CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshTokenCookie())
                .body(ApiResponse.ok("로그아웃 되었습니다."));
    }

    @GetMapping("/me")
    public ApiResponse<AuthResponse.UserResponse> me(Authentication authentication) {
        return ApiResponse.ok(authService.getCurrentUser(authentication.getName()));
    }

    private AuthResponse.TokenResponse withoutRefreshToken(AuthResponse.TokenResponse tokenResponse) {
        return new AuthResponse.TokenResponse(
                tokenResponse.accessToken(),
                null,
                tokenResponse.tokenType(),
                tokenResponse.expiresIn()
        );
    }

    private String createRefreshTokenCookie(String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ofMillis(refreshTokenExpirationMs))
                .build();
        return cookie.toString();
    }

    private String clearAnonymousOwnerCookie() {
        ResponseCookie cookie = ResponseCookie.from(ANONYMOUS_OWNER_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        return cookie.toString();
    }

    private String clearRefreshTokenCookie() {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        return cookie.toString();
    }
}