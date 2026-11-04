package com.andrija.homesiloserver.storage;

import com.andrija.homesiloserver.file.FileMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;

/**
 * Removes blobs from disk once nothing references them. Runs only after the
 * deleting transaction commits, so a rollback can never leave rows pointing at
 * missing files. Anything missed here (e.g. a crash) is picked up by the nightly
 * {@link #sweep(Duration)}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlobGarbageCollector {

    /** Blobs written more recently than this may be about to be referenced by an in-flight upload. */
    private static final Duration UPLOAD_GRACE = Duration.ofMinutes(5);

    private final BlobStorage blobStorage;
    private final FileMetadataRepository fileMetadataRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBlobsReleased(BlobsReleasedEvent event) {
        for (String hash : event.hashes()) {
            try {
                if (!fileMetadataRepository.existsByOwnerIdAndStoredFileName(event.ownerId(), hash)) {
                    blobStorage.deleteIfIdle(event.ownerId(), hash, UPLOAD_GRACE);
                }
            } catch (RuntimeException ex) {
                log.warn("Could not delete blob {} for owner {}; the nightly sweep will retry",
                        hash, event.ownerId(), ex);
            }
        }
    }

    public int sweep(Duration minAge) {
        int removed = blobStorage.sweepOrphans(fileMetadataRepository::existsByOwnerIdAndStoredFileName, minAge);
        if (removed > 0) {
            log.info("Orphan sweep removed {} unreferenced blob(s)", removed);
        }
        return removed;
    }
}
