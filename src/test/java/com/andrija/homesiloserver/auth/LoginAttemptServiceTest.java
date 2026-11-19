package com.andrija.homesiloserver.auth;

import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.common.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    /** 3 per account and address, 5 per address, 6 per account. */
    private final LoginAttemptService attempts = new LoginAttemptService(properties());

    @Test
    void guessingOnePasswordFromOneAddressIsStopped() {
        fail("alice", "10.0.0.1", 3);
        assertBlocked("alice", "10.0.0.1");
        assertThatCode(() -> attempts.assertAllowed("alice", "10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    void oneAddressTryingManyAccountsIsStopped() {
        for (int i = 0; i < 5; i++) fail("user" + i, "10.0.0.9", 1);
        assertBlocked("someone-new", "10.0.0.9");
        assertThatCode(() -> attempts.assertAllowed("someone-new", "10.0.0.10")).doesNotThrowAnyException();
    }

    @Test
    void oneAccountGuessedFromManyAddressesIsStopped() {
        for (int i = 0; i < 6; i++) fail("bob", "10.1.0." + i, 1);
        assertBlocked("bob", "10.2.0.1");
    }

    @Test
    void aCorrectPasswordClearsOnlyThatPair() {
        fail("carol", "10.0.0.3", 2);
        attempts.recordSuccess("carol", "10.0.0.3");
        fail("carol", "10.0.0.3", 2);
        assertThatCode(() -> attempts.assertAllowed("carol", "10.0.0.3")).doesNotThrowAnyException();
    }

    @Test
    void madeUpUsernamesCantGrowItWithoutBound() {
        LoginAttemptService roomy = new LoginAttemptService(properties(1_000_000, 1_000_000));
        for (int i = 0; i < LoginAttemptService.MAX_TRACKED; i++) roomy.recordFailure("u" + i, "10.3." + (i % 250));
        assertThat(roomy.tracked()).isLessThanOrEqualTo(LoginAttemptService.MAX_TRACKED + 3);
    }

    private void fail(String username, String address, int times) {
        for (int i = 0; i < times; i++) attempts.recordFailure(username, address);
    }

    private void assertBlocked(String username, String address) {
        assertThatThrownBy(() -> attempts.assertAllowed(username, address))
                .isInstanceOf(TooManyRequestsException.class);
    }

    private static HomesiloProperties properties() {
        return properties(5, 6);
    }

    private static HomesiloProperties properties(int perAddress, int perAccount) {
        return new HomesiloProperties(
                new HomesiloProperties.Jwt("x".repeat(32), Duration.ofHours(1)),
                new HomesiloProperties.Auth("s", false, false, 3, Duration.ofMinutes(15), "", perAddress, perAccount),
                new HomesiloProperties.Storage("target/unused", DataSize.ofGigabytes(1), "target/unused-thumbnails"),
                new HomesiloProperties.Trash(30, "0 0 3 * * *"),
                new HomesiloProperties.Cors(List.of()),
                new HomesiloProperties.Activity(90));
    }
}
