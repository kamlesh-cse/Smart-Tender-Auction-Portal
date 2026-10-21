package com.smartportal.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final int maximumAttempts;
    private final Duration lockDuration;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(
            @Value("${app.security.login.max-attempts:5}") int maximumAttempts,
            @Value("${app.security.login.lock-duration:PT15M}") Duration lockDuration) {
        this(maximumAttempts, lockDuration, Clock.systemUTC());
    }

    LoginAttemptService(int maximumAttempts, Duration lockDuration, Clock clock) {
        this.maximumAttempts = maximumAttempts;
        this.lockDuration = lockDuration;
        this.clock = clock;
    }

    public boolean isLocked(String loginId) {
        Attempt attempt = attempts.get(loginId);
        if (attempt == null || attempt.lockedUntil == null) {
            return false;
        }
        if (!attempt.lockedUntil.isAfter(clock.instant())) {
            attempts.remove(loginId, attempt);
            return false;
        }
        return true;
    }

    public void failed(String loginId) {
        attempts.compute(loginId, (key, current) -> {
            int failures = current == null ? 1 : current.failures + 1;
            Instant lockedUntil = failures >= maximumAttempts
                    ? clock.instant().plus(lockDuration) : null;
            return new Attempt(failures, lockedUntil);
        });
    }

    public void succeeded(String loginId) {
        attempts.remove(loginId);
    }

    private record Attempt(int failures, Instant lockedUntil) {
    }
}
