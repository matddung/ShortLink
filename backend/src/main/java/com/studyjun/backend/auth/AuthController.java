package com.studyjun.backend.auth;

import com.studyjun.backend.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ApiResponse<AuthResponse.UserResponse> signup(@Valid @RequestBody AuthRequest.SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse.TokenResponse> login(@Valid @RequestBody AuthRequest.LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse.TokenResponse> refresh(@Valid @RequestBody AuthRequest.RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout(@Valid @RequestBody AuthRequest.RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok("로그아웃 되었습니다.");
    }

    @GetMapping("/me")
    public ApiResponse<AuthResponse.UserResponse> me(Authentication authentication) {
        return ApiResponse.ok(authService.getCurrentUser(authentication.getName()));
    }
}