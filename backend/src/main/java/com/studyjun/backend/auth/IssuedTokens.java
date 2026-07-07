package com.studyjun.backend.auth;

public record IssuedTokens(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
}
