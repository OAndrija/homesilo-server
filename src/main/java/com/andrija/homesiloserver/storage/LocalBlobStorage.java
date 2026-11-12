package com.andrija.homesiloserver.storage;

import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.common.exception.StorageException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

/**
 * Stores blobs on the local filesystem as {@code <base>/<ownerId>/<sha256>}.
 * Uploads are first written to {@code <base>/.tmp} and then atomically renamed
 * into place, so a crash mid-upload never leaves a truncated blob behind.
 */
@Slf4j
@Service
public class LocalBlobStorage implements BlobStorage {

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");
    private static final Duration STALE_UPLOAD_AGE = Duration.ofDays(1);

    private final Path root;
    private final Path tmpDir;

    public LocalBlobStorage(HomesiloProperties properties) {
        this.root = Paths.get(properties.storage().basePath()).toAbsolutePath().normalize();
        this.tmpDir = root.resolve(".tmp");
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(tmpDir);
        } catch (IOException ex) {
            throw new StorageException("Could not initialise storage at " + root, ex);
        }
        log.info("Blob storage at {}", root);
    }

    @Override
    public StoredBlob store(UUID ownerId, InputStream content) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile(tmpDir, "upload-", ".part");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (InputStream in = new DigestInputStream(content, digest);
                 OutputStream out = Files.newOutputStream(tmp)) {
                size = in.transferTo(out);
            }
            String hash = HexFormat.of().formatHex(digest.digest());
            Path target = blobPath(ownerId, hash);
            Files.createDirectories(target.getParent());

            if (Files.exists(target)) {
                // Same bytes already stored: drop the copy, and bump the mtime so the
                // orphan sweep treats the blob as freshly referenced.
                Files.delete(tmp);
                Files.setLastModifiedTime(target, FileTime.from(Instant.now()));
            } else {
                moveIntoPlace(tmp, target);
            }
            return new StoredBlob(hash, size);
        } catch (IOException | NoSuchAlgorithmException ex) {
            deleteQuietly(tmp);
            throw new StorageException("Failed to store upload", ex);
        }
    }

    @Override
    public Resource load(UUID ownerId, String hash) {
        Path path = blobPath(ownerId, hash);
        if (!Files.isReadable(path)) {
            throw new StorageException("Blob " + hash + " for owner " + ownerId + " is missing from disk");
        }
        return new FileSystemResource(path);
    }

    @Override
    public boolean deleteIfIdle(UUID ownerId, String hash, Duration grace) {
        Path path = blobPath(ownerId, hash);
        try {
            if (!Files.exists(path) || !olderThan(path, Instant.now().minus(grace))) {
                return false;
            }
            boolean deleted = Files.deleteIfExists(path);
            if (deleted) log.debug("Deleted blob {} for owner {}", hash, ownerId);
            return deleted;
        } catch (IOException ex) {
            throw new StorageException("Failed to delete blob " + hash, ex);
        }
    }

    @Override
    public int sweepOrphans(BiPredicate<UUID, String> isReferenced, Duration minAge) {
        Instant cutoff = Instant.now().minus(minAge);
        int removed = 0;
        try (DirectoryStream<Path> owners = Files.newDirectoryStream(root, Files::isDirectory)) {
            for (Path ownerDir : owners) {
                UUID ownerId = parseUuid(ownerDir.getFileName().toString());
                if (ownerId == null) continue; // .tmp and anything else that isn't ours
                try (DirectoryStream<Path> blobs = Files.newDirectoryStream(ownerDir)) {
                    for (Path blob : blobs) {
                        String hash = blob.getFileName().toString();
                        if (SHA256_HEX.matcher(hash).matches()
                                && olderThan(blob, cutoff)
                                && !isReferenced.test(ownerId, hash)) {
                            Files.deleteIfExists(blob);
                            removed++;
                        }
                    }
                }
            }
        } catch (IOException ex) {
            log.warn("Orphan sweep aborted", ex);
        }
        removed += sweepStaleUploads();
        return removed;
    }

    private int sweepStaleUploads() {
        Instant cutoff = Instant.now().minus(STALE_UPLOAD_AGE);
        int removed = 0;
        try (DirectoryStream<Path> parts = Files.newDirectoryStream(tmpDir)) {
            for (Path part : parts) {
                if (olderThan(part, cutoff) && Files.deleteIfExists(part)) removed++;
            }
        } catch (IOException ex) {
            log.warn("Could not clean temporary uploads", ex);
        }
        return removed;
    }

    private Path blobPath(UUID ownerId, String hash) {
        if (hash == null || !SHA256_HEX.matcher(hash).matches()) {
            throw new StorageException("Invalid blob id");
        }
        return root.resolve(ownerId.toString()).resolve(hash);
    }

    private static void moveIntoPlace(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException ex) {
            Files.deleteIfExists(tmp); // a concurrent upload of the same bytes won the race
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean olderThan(Path path, Instant cutoff) throws IOException {
        return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort; the stale-upload sweep will retry
        }
    }
}
