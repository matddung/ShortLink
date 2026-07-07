package com.studyjun.backend.link.clickevent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

class CooldownBackoff {

    private final Clock clock;
    private final Duration cooldown;
    private volatile Instant disabledUntil = Instant.MIN;

    CooldownBackoff(Duration cooldown) {
        this(Clock.systemUTC(), cooldown);
    }

    CooldownBackoff(Clock clock, Duration cooldown) {
        this.clock = clock;
        this.cooldown = cooldown == null || cooldown.isNegative() ? Duration.ZERO : cooldown;
    }

    boolean isAvailable() {
        return !Instant.now(clock).isBefore(disabledUntil);
    }

    void markFailure() {
        disabledUntil = Instant.now(clock).plus(cooldown);
    }

    void markSuccess() {
        disabledUntil = Instant.MIN;
    }
}
