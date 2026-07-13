package com.studyjun.backend.link.application.command;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class ShortCodeService {

    private static final String CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SHORT_CODE_LENGTH = 6;

    private final SecureRandom secureRandom = new SecureRandom();

    public String resolveShortCode(String customCode) {
        if (customCode != null && !customCode.isBlank()) {
            return customCode;
        }

        return generateShortCodeCandidate();
    }

    public String generateShortCodeCandidate() {
        return randomCode();
    }

    private String randomCode() {
        StringBuilder builder = new StringBuilder(SHORT_CODE_LENGTH);
        for (int i = 0; i < SHORT_CODE_LENGTH; i++) {
            builder.append(CHARACTERS.charAt(secureRandom.nextInt(CHARACTERS.length())));
        }
        return builder.toString();
    }
}
