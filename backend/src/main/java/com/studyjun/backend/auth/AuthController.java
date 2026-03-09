package com.studyjun.backend.auth;

import com.studyjun.backend.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

    private final AuthService authService;
    private final boolean secureCookie;
    private final String sameSite;
    private final long refreshTokenExpirationMs;

    public AuthController(AuthService authService,
                          @Value("${app.auth.secure-cookie:true}") boolean secureCookie,
                          @Value("${app.auth.same-site:Lax}") String sameSite,
                          @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        this.authService = authService;
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @PostMapping("/signup")
    public ApiResponse<AuthResponse.UserResponse> signup(@Valid @RequestBody AuthRequest.SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse.TokenResponse>> login(@Valid @RequestBody AuthRequest.LoginRequest request) {
        AuthResponse.TokenResponse tokenResponse = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, createRefreshTokenCookie(tokenResponse.refreshToken()))
                .body(ApiResponse.ok(withoutRefreshToken(tokenResponse)));
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