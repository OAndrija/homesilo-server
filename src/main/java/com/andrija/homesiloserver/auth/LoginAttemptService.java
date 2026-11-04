package com.andrija.homesiloserver.auth;

import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.common.exception.TooManyRequestsException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force protection: after {@code maxLoginAttempts} failures for the same
 * username from the same address within the lockout window, further attempts
 * are rejected with 429 until the window passes. In-memory by design — HomeSilo
 * runs as a single instance.
 */
@Service
public class LoginAttemptService {

    private final int maxAttempts;
    private final Duration lockout;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(HomesiloProperties properties) {
        this.maxAttempts = properties.auth().maxLoginAttempts();
        this.lockout = properties.auth().loginLockout();
    }

    public void assertAllowed(String username, String clientAddress) {
        Attempts current = attempts.get(key(username, clientAddress));
        if (current == null || current.lockedUntil() == null) return;

        Instant now = Instant.now();
        if (now.isBefore(current.lockedUntil())) {
            throw new TooManyRequestsException(
                    "Too many failed sign-in attempts. Try again later.",
                    Duration.between(now, current.lockedUntil()));
        }
        attempts.remove(key(username, clientAddress));
    }

    public void recordFailure(String username, String clientAddress) {
        Instant now = Instant.now();
        attempts.compute(key(username, clientAddress), (k, previous) -> {
            boolean windowExpired = previous == null || previous.firstFailure().plus(lockout).isBefore(now);
            int failures = windowExpired ? 1 : previous.failures() + 1;
            Instant first = windowExpired ? now : previous.firstFailure();
            Instant lockedUntil = failures >= maxAttempts ? now.plus(lockout) : null;
            return new Attempts(failures, first, lockedUntil);
        });
    }

    public void recordSuccess(String username, String clientAddress) {
        attempts.remove(key(username, clientAddress));
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void evictExpired() {
        Instant now = Instant.now();
        attempts.values().removeIf(a -> a.firstFailure().plus(lockout).isBefore(now)
                && (a.lockedUntil() == null || a.lockedUntil().isBefore(now)));
    }

    private static String key(String username, String clientAddress) {
        return username.trim().toLowerCase(Locale.ROOT) + '|' + clientAddress;
    }

    private record Attempts(int failures, Instant firstFailure, Instant lockedUntil) {}
}
