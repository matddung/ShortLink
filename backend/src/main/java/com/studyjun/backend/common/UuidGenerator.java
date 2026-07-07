package com.studyjun.backend.common;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UuidGenerator {

    public String generateString() {
        return UUID.randomUUID().toString();
    }
}
