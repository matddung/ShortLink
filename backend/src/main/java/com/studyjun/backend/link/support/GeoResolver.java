package com.studyjun.backend.link.support;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GeoResolver {

    private final List<String> countryHeaderCandidates;
    private final Map<String, String> languageCountryFallbacks;

    public GeoResolver(@Value("${app.geo.country-headers:CF-IPCountry,CloudFront-Viewer-Country,X-AppEngine-Country,X-Country-Code,X-Vercel-IP-Country,Fastly-Geo-Country-Code,X-Geo-Country}") String countryHeaders,
                       @Value("${app.geo.language-country-fallbacks:ko:KR,ja:JP,en:US}") String languageCountryFallbacks) {
        this.countryHeaderCandidates = List.of(countryHeaders.split(","))
                .stream()
                .map(String::trim)
                .filter(header -> !header.isBlank())
                .toList();
        this.languageCountryFallbacks = List.of(languageCountryFallbacks.split(","))
                .stream()
                .map(String::trim)
                .filter(entry -> entry.contains(":"))
                .map(entry -> entry.split(":", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(
                        parts -> parts[0].trim().toLowerCase(),
                        parts -> {
                            String normalizedCountry = normalizeCountryCode(parts[1]);
                            return normalizedCountry == null ? "" : normalizedCountry;
                        },
                        (left, right) -> right
                ));
    }

    public String resolveCountryCode(HttpServletRequest request) {
        for (String header : countryHeaderCandidates) {
            String country = normalizeCountryCode(request.getHeader(header));
            if (country != null) {
                return country;
            }
        }

        String acceptLanguage = request.getHeader("Accept-Language");
        if (acceptLanguage != null && !acceptLanguage.isBlank()) {
            String firstTag = acceptLanguage.split(",")[0].trim();
            Locale locale = Locale.forLanguageTag(firstTag);
            String fromTag = normalizeCountryCode(locale.getCountry());
            if (fromTag != null) {
                return fromTag;
            }

            if (firstTag.contains("-")) {
                String[] parts = firstTag.split("-");
                if (parts.length > 1) {
                    String fromRaw = normalizeCountryCode(parts[parts.length - 1]);
                    if (fromRaw != null) {
                        return fromRaw;
                    }
                }
            }

            String lang = locale.getLanguage();
            if (lang != null && !lang.isBlank()) {
                String mappedCountry = normalizeCountryCode(languageCountryFallbacks.get(lang.toLowerCase()));
                if (mappedCountry != null) {
                    return mappedCountry;
                }
            }
        }

        String localeCountry = normalizeCountryCode(request.getLocale() != null ? request.getLocale().getCountry() : null);
        if (localeCountry != null) {
            return localeCountry;
        }

        if (log.isDebugEnabled()) {
            String geoHeaderDump = countryHeaderCandidates.stream()
                    .map(header -> header + "=" + request.getHeader(header))
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("none");
            log.debug("Country detection fallback to Unknown. geoHeaders=[{}], Accept-Language='{}', requestLocale='{}', remoteAddr='{}', xForwardedFor='{}', languageCountryFallbacks={}",
                    geoHeaderDump,
                    request.getHeader("Accept-Language"),
                    request.getLocale(),
                    request.getRemoteAddr(),
                    request.getHeader("X-Forwarded-For"),
                    languageCountryFallbacks
            );
        }

        return "Unknown";
    }

    private String normalizeCountryCode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.length() == 2 && normalized.chars().allMatch(Character::isLetter)) {
            return normalized;
        }
        return null;
    }
}