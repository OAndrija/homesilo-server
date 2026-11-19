package com.andrija.homesiloserver.auth;

import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.common.exception.TooManyRequestsException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force protection for sign-in (and setup), counting failures three ways
 * within the lockout window:
 * <ul>
 *   <li>one account from one address: {@code maxLoginAttempts} (someone guessing a password);</li>
 *   <li>one address, any account: {@code maxFailuresPerAddress} (one password tried
 *       against many accounts, "password spraying");</li>
 *   <li>one account, any address: {@code maxFailuresPerAccount} (guessing spread over
 *       many addresses).</li>
 * </ul>
 * Past any of them, further attempts get 429 until the window passes. In memory
 * by design, as HomeSilo runs as a single instance; the number of tracked keys is
 * capped so a flood of made-up usernames can't grow it without bound.
 */
@Service
public class LoginAttemptService {

    /** Far more than real use needs; past it, entries that aren't blocking anything are dropped. */
    static final int MAX_TRACKED = 50_000;

    private final int maxPerAccountAndAddress;
    private final int maxPerAddress;
    private final int maxPerAccount;
    private final Duration lockout;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(HomesiloProperties properties) {
        HomesiloProperties.Auth auth = properties.auth();
        this.maxPerAccountAndAddress = auth.maxLoginAttempts();
        this.maxPerAddress = auth.maxFailuresPerAddress();
        this.maxPerAccount = auth.maxFailuresPerAccount();
        this.lockout = auth.loginLockout();
    }

    public void assertAllowed(String username, String clientAddress) {
        Instant now = Instant.now();
        for (String key : keys(username, clientAddress)) {
            Attempts current = attempts.get(key);
            if (current != null && current.lockedUntil() != null && now.isBefore(current.lockedUntil())) {
                throw new TooManyRequestsException(
                        "Too many failed sign-in attempts. Try again later.",
                        Duration.between(now, current.lockedUntil()));
            }
        }
    }

    public void recordFailure(String username, String clientAddress) {
        if (attempts.size() >= MAX_TRACKED) evictExpired();
        if (attempts.size() >= MAX_TRACKED) attempts.values().removeIf(a -> a.lockedUntil() == null);

        Instant now = Instant.now();
        List<String> keys = keys(username, clientAddress);
        int[] limits = {maxPerAccountAndAddress, maxPerAddress, maxPerAccount};
        for (int i = 0; i < keys.size(); i++) {
            int limit = limits[i];
            attempts.compute(keys.get(i), (k, previous) -> {
                boolean windowExpired = previous == null || previous.firstFailure().plus(lockout).isBefore(now);
                int failures = windowExpired ? 1 : previous.failures() + 1;
                Instant first = windowExpired ? now : previous.firstFailure();
                Instant lockedUntil = failures >= limit ? now.plus(lockout) : null;
                return new Attempts(failures, first, lockedUntil);
            });
        }
    }

    /**
     * Clears this account-and-address pair. The wider counts stay: a correct
     * password doesn't excuse the guesses at other accounts from the same place.
     */
    public void recordSuccess(String username, String clientAddress) {
        attempts.remove(keys(username, clientAddress).get(0));
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void evictExpired() {
        Instant now = Instant.now();
        attempts.values().removeIf(a -> a.firstFailure().plus(lockout).isBefore(now)
                && (a.lockedUntil() == null || a.lockedUntil().isBefore(now)));
    }

    int tracked() {
        return attempts.size();
    }

    /** Pair, address, account: the same order as the limits in {@link #recordFailure}. */
    private static List<String> keys(String username, String clientAddress) {
        String account = username.trim().toLowerCase(Locale.ROOT);
        return List.of("pair|" + account + '|' + clientAddress, "address|" + clientAddress, "account|" + account);
    }

    private record Attempts(int failures, Instant firstFailure, Instant lockedUntil) {}
}
