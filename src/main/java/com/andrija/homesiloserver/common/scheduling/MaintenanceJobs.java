package com.andrija.homesiloserver.common.scheduling;

import com.andrija.homesiloserver.storage.BlobGarbageCollector;
import com.andrija.homesiloserver.trash.TrashService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Nightly housekeeping: enforce trash retention, then reclaim any unreferenced blobs. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceJobs {

    private static final Duration ORPHAN_MIN_AGE = Duration.ofHours(1);

    private final TrashService trashService;
    private final BlobGarbageCollector blobGarbageCollector;

    @Scheduled(cron = "${homesilo.trash.purge-cron:0 30 3 * * *}")
    public void nightly() {
        try {
            trashService.purgeExpired();
        } catch (RuntimeException ex) {
            log.error("Trash purge failed", ex);
        }
        try {
            blobGarbageCollector.sweep(ORPHAN_MIN_AGE);
        } catch (RuntimeException ex) {
            log.error("Orphan blob sweep failed", ex);
        }
    }
}
