package com.studyjun.backend.link.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class LinkRequest {

    public record CreateAnonymousRequest(
            @NotBlank(message = "URL is required")
            String originalUrl
    ) {
    }

    public record CreateLinkRequest(
            @NotBlank(message = "URL is required")
            String originalUrl,
            @Pattern(regexp = "^[a-zA-Z0-9-]{3,32}$", message = "customCode must be 3 to 32 characters and contain only letters, numbers, or hyphens")
            String customCode
    ) {
    }

    public record UpdateLinkStatusRequest(
            @NotBlank(message = "status is required")
            @Pattern(regexp = "active|inactive", message = "status must be active or inactive")
            String status
    ) {
        public boolean active() {
            return "active".equals(status);
        }
    }
}
