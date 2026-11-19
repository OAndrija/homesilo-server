package com.andrija.homesiloserver.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratedSecretTest {

    @TempDir
    Path dir;

    @Test
    void madeOnceThenReused() throws Exception {
        Path secrets = dir.resolve(".secrets");
        String first = GeneratedSecret.loadOrCreate(secrets, "jwt-secret");
        String again = GeneratedSecret.loadOrCreate(secrets, "jwt-secret");

        assertThat(first).hasSizeGreaterThanOrEqualTo(64).isEqualTo(again);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(secrets.resolve("jwt-secret"))))
                .isEqualTo("rw-------");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(secrets))).isEqualTo("rwx------");
    }

    @Test
    void aDamagedFileIsntUsed() throws Exception {
        Files.writeString(dir.resolve("jwt-secret"), "short");
        assertThatThrownBy(() -> GeneratedSecret.loadOrCreate(dir, "jwt-secret"))
                .hasMessageContaining("set JWT_SECRET");
    }
}
