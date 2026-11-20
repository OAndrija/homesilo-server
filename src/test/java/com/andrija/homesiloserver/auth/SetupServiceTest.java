package com.andrija.homesiloserver.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SetupServiceTest {

    @Test
    void theCodeCanBeTypedLooselyButMustBeExact() {
        assertThat(SetupService.matches("abcd-efgh-2345", "ABCD-EFGH-2345")).isTrue();
        assertThat(SetupService.matches(" ABCD EFGH 2345 ", "ABCD-EFGH-2345")).isTrue();
        assertThat(SetupService.matches("ABCD-EFGH-2346", "ABCD-EFGH-2345")).isFalse();
        assertThat(SetupService.matches("ABCD-EFGH", "ABCD-EFGH-2345")).isFalse();
        assertThat(SetupService.matches("", "ABCD-EFGH-2345")).isFalse();
        assertThat(SetupService.matches(null, "ABCD-EFGH-2345")).isFalse();
    }
}
