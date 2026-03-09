package com.studyjun.backend.common;

import java.time.LocalDateTime;

public record ErrorResponse(
        boolean success,
        String code,
        String message,
        LocalDateTime timestamp
) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(false, code, message, LocalDateTime.now());
    }
}