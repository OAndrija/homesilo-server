package com.andrija.homesiloserver.storage;

import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.common.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalBlobStorageTest {

    @TempDir
    Path root;

    private LocalBlobStorage storage;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        HomesiloProperties properties = new HomesiloProperties(
                new HomesiloProperties.Jwt("x".repeat(32), Duration.ofHours(1)),
                new HomesiloProperties.Auth("s", false, true, 5, Duration.ofMinutes(1)),
                new HomesiloProperties.Storage(root.toString(), DataSize.ofGigabytes(1)),
                new HomesiloProperties.Trash(30, "0 0 3 * * *"),
                new HomesiloProperties.Cors(List.of()),
                new HomesiloProperties.Activity(90));
        storage = new LocalBlobStorage(properties);
        storage.init();
    }

    @Test
    void storesContentUnderItsSha256AndDeduplicates() throws Exception {
        var first = storage.store(owner, stream("hello"));
        var second = storage.store(owner, stream("hello"));

        assertThat(first.hash())
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824")
                .isEqualTo(second.hash());
        assertThat(first.size()).isEqualTo(5);
        assertThat(storage.load(owner, first.hash()).getContentAsString(StandardCharsets.UTF_8)).isEqualTo("hello");
        try (var files = Files.list(root.resolve(".tmp"))) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void recentlyWrittenBlobsSurviveDeleteIfIdle() {
        var blob = storage.store(owner, stream("fresh"));
        assertThat(storage.deleteIfIdle(owner, blob.hash(), Duration.ofMinutes(5))).isFalse();
        assertThat(storage.deleteIfIdle(owner, blob.hash(), Duration.ZERO)).isTrue();
    }

    @Test
    void sweepRemovesOnlyUnreferencedBlobs() {
        var kept = storage.store(owner, stream("kept"));
        var orphan = storage.store(owner, stream("orphan"));

        int removed = storage.sweepOrphans((o, hash) -> hash.equals(kept.hash()), Duration.ZERO);

        assertThat(removed).isEqualTo(1);
        assertThat(storage.load(owner, kept.hash()).exists()).isTrue();
        assertThatThrownBy(() -> storage.load(owner, orphan.hash())).isInstanceOf(StorageException.class);
    }

    @Test
    void rejectsBlobIdsThatCouldEscapeTheStorageRoot() {
        assertThatThrownBy(() -> storage.load(owner, "../../etc/passwd")).isInstanceOf(StorageException.class);
    }

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
