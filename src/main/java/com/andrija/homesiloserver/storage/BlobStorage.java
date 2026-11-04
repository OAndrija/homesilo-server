package com.andrija.homesiloserver.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Content-addressed blob store. Each blob is identified by the SHA-256 of its
 * bytes and namespaced per owner, so identical uploads by the same user share
 * one copy on disk while several file entries (in different folders, under
 * different names) can reference it.
 */
public interface BlobStorage {

    /** Streams {@code content} to storage, hashing it on the way. Never buffers the whole file in memory. */
    StoredBlob store(UUID ownerId, InputStream content);

    Resource load(UUID ownerId, String hash);

    /**
     * Deletes the blob unless it was written within {@code grace} — a recent write
     * means a concurrent upload may be about to reference it. Returns whether it was deleted.
     */
    boolean deleteIfIdle(UUID ownerId, String hash, Duration grace);

    /**
     * Deletes blobs that {@code isReferenced} reports as unused and that are older
     * than {@code minAge}, plus abandoned partial uploads. Returns the number of
     * blobs removed.
     */
    int sweepOrphans(BiPredicate<UUID, String> isReferenced, Duration minAge);

    record StoredBlob(String hash, long size) {}
}
