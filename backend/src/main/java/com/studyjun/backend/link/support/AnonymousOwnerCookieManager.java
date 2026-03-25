package com.studyjun.backend.link.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class AnonymousOwnerCookieManager {

    private static final String ANONYMOUS_OWNER_COOKIE = "anonymous_owner";

    private final boolean secureCookie;
    private final String sameSite;
    private final long anonymousExpirationDays;

    public AnonymousOwnerCookieManager(@Value("${app.auth.secure-cookie:true}") boolean secureCookie,
                                       @Value("${app.auth.same-site:Lax}") String sameSite,
                                       @Value("${app.anonymous.expiration-days:30}") long anonymousExpirationDays) {
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
        this.anonymousExpirationDays = anonymousExpirationDays;
    }

    public String normalizeOwnerKey(String ownerKey) {
        if (ownerKey == null || ownerKey.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return ownerKey;
    }

    public String createOwnerCookie(String ownerKey) {
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