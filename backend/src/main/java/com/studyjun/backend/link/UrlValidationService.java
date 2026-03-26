package com.studyjun.backend.link;

import com.studyjun.backend.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

@Component
public class UrlValidationService {

    public void validate(String originalUrl) {
        try {
            URI uri = new URI(originalUrl);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw invalidUrlException();
            }

            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw invalidUrlException();
            }
        } catch (URISyntaxException e) {
            throw invalidUrlException();
        }
    }

    private BusinessException invalidUrlException() {
        return new BusinessException("INVALID_URL", "올바른 URL을 입력해 주세요.", HttpStatus.BAD_REQUEST);
    }
}