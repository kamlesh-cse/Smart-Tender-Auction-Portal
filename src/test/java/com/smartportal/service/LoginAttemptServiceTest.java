package com.smartportal.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {

    @Test
    void locksLoginAfterConfiguredFailures() {
        LoginAttemptService service = new LoginAttemptService(
                3, Duration.ofMinutes(15),
                Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC));

        service.failed("admin");
        service.failed("admin");
        assertFalse(service.isLocked("admin"));

        service.failed("admin");
        assertTrue(service.isLocked("admin"));
    }

    @Test
    void successfulLoginClearsFailures() {
        LoginAttemptService service = new LoginAttemptService(
                2, Duration.ofMinutes(15),
                Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC));

        service.failed("user@example.com");
        service.succeeded("user@example.com");
        service.failed("user@example.com");

        assertFalse(service.isLocked("user@example.com"));
    }
}
