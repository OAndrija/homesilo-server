package com.andrija.homesiloserver.auth;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * A secret the server makes for itself on first start, so an install doesn't
 * depend on someone choosing a strong one. It's kept in a file only the server's
 * user can read, next to the stored files, and reused on every start after.
 * Losing it (a new data volume) only signs everyone out.
 */
@Slf4j
final class GeneratedSecret {

    private GeneratedSecret() {
    }

    static String loadOrCreate(Path dir, String name) {
        Path file = dir.resolve(name);
        try {
            if (Files.exists(file)) return read(file);
            createPrivateDirectory(dir);
            byte[] bytes = new byte[64];
            new SecureRandom().nextBytes(bytes);
            String secret = Base64.getEncoder().encodeToString(bytes);
            try {
                Files.writeString(file, secret, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
                restrict(file, "rw-------");
                log.info("Generated a new {} in {}", name, dir);
                return secret;
            } catch (FileAlreadyExistsException ex) {
                return read(file); // another instance made it first
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read or create " + file
                    + ". Make it writable for the server, or set JWT_SECRET.", ex);
        }
    }

    private static String read(Path file) throws IOException {
        String secret = Files.readString(file, StandardCharsets.US_ASCII).trim();
        if (secret.length() < 32) throw new IOException(file + " is too short to be a secret");
        return secret;
    }

    private static void createPrivateDirectory(Path dir) throws IOException {
        Files.createDirectories(dir);
        restrict(dir, "rwx------");
    }

    private static void restrict(Path path, String permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
        } catch (UnsupportedOperationException ex) {
            // Not a POSIX file system (Windows during development): nothing to set.
        }
    }
}
