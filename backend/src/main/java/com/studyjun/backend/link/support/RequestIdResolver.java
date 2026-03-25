package com.studyjun.backend.link.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RequestIdResolver {

    public String resolve(HttpServletRequest request) {
        String requestIdHeader = request.getHeader("X-Request-Id");
        if (isUsableRequestId(requestIdHeader)) {
            return requestIdHeader;
        }

        String requestId = request.getRequestId();
        if (isUsableRequestId(requestId)) {
            return requestId;
        }

        return UUID.randomUUID().toString();
    }

    private boolean isUsableRequestId(String requestId) {
        return requestId != null
                && !requestId.isBlank()
                && !"0".equals(requestId.trim());
    }
}