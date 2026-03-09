package com.studyjun.backend.auth;

public class AuthResponse {

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn
    ) {
    }

    public record UserResponse(
            Long id,
            String email,
            String name
    ) {
    }
}