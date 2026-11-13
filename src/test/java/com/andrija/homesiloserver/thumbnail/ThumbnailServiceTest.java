package com.andrija.homesiloserver.thumbnail;

import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.storage.BlobStorage.StoredBlob;
import com.andrija.homesiloserver.storage.LocalBlobStorage;
import com.andrija.homesiloserver.support.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ThumbnailServiceTest {

    @TempDir
    Path dir;

    private LocalBlobStorage blobs;
    private ThumbnailService thumbnails;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        HomesiloProperties properties = new HomesiloProperties(
                new HomesiloProperties.Jwt("x".repeat(32), Duration.ofHours(1)),
                new HomesiloProperties.Auth("s", false, true, 5, Duration.ofMinutes(1), ""),
                new HomesiloProperties.Storage(dir.resolve("blobs").toString(), DataSize.ofGigabytes(1),
                        dir.resolve("thumbs").toString()),
                new HomesiloProperties.Trash(30, "0 0 3 * * *"),
                new HomesiloProperties.Cors(List.of()),
                new HomesiloProperties.Activity(90));
        blobs = new LocalBlobStorage(properties);
        blobs.init();
        thumbnails = new ThumbnailService(blobs, properties);
    }

    @Test
    void largePhotosShrinkToTheThumbnailSizeKeepingTheirShape() throws Exception {
        StoredBlob blob = store(TestImages.jpeg(TestImages.halves(3000, 1500, false)));

        var thumb = thumbnails.get(owner, blob.hash(), "image/jpeg", blob.size()).orElseThrow();

        assertThat(thumb.type()).isEqualTo(MediaType.IMAGE_JPEG);
        BufferedImage image = ImageIO.read(thumb.path().toFile());
        assertThat(image.getWidth()).isEqualTo(ThumbnailService.SIZE);
        assertThat(image.getHeight()).isEqualTo(ThumbnailService.SIZE / 2);
    }

    @Test
    void sidewaysPhonePhotosAreTurnedUpright() throws Exception {
        // Stored landscape (red left, blue right) with "turn 90° clockwise" in EXIF.
        StoredBlob blob = store(TestImages.jpegWithOrientation(TestImages.halves(1000, 500, false), 6));

        BufferedImage image = ImageIO.read(thumbnails.get(owner, blob.hash(), "image/jpeg", blob.size())
                .orElseThrow().path().toFile());

        assertThat(image.getWidth()).isEqualTo(240);
        assertThat(image.getHeight()).isEqualTo(480);
        // Turned clockwise, the red half ends up on top and the blue half at the bottom.
        assertThat(isMostly(image.getRGB(120, 60), Color.RED)).isTrue();
        assertThat(isMostly(image.getRGB(120, 420), Color.BLUE)).isTrue();
    }

    @Test
    void transparentImagesStayPng() throws Exception {
        StoredBlob blob = store(TestImages.png(TestImages.halves(800, 800, true)));

        var thumb = thumbnails.get(owner, blob.hash(), "image/png", blob.size()).orElseThrow();

        assertThat(thumb.type()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(ImageIO.read(thumb.path().toFile()).getColorModel().hasAlpha()).isTrue();
    }

    @Test
    void smallImagesAreNotEnlarged() throws Exception {
        StoredBlob blob = store(TestImages.png(TestImages.halves(120, 80, false)));
        BufferedImage image = ImageIO.read(thumbnails.get(owner, blob.hash(), "image/png", blob.size())
                .orElseThrow().path().toFile());
        assertThat(image.getWidth()).isEqualTo(120);
        assertThat(image.getHeight()).isEqualTo(80);
    }

    @Test
    void isCachedAndEvictedWithItsBlob() throws Exception {
        StoredBlob blob = store(TestImages.png(TestImages.halves(600, 600, false)));
        Path first = thumbnails.get(owner, blob.hash(), "image/png", blob.size()).orElseThrow().path();
        long made = Files.getLastModifiedTime(first).toMillis();

        Path second = thumbnails.get(owner, blob.hash(), "image/png", blob.size()).orElseThrow().path();
        assertThat(second).isEqualTo(first);
        assertThat(Files.getLastModifiedTime(second).toMillis()).isEqualTo(made);

        thumbnails.evict(owner, blob.hash());
        assertThat(first).doesNotExist();
    }

    @Test
    void sweepRemovesThumbnailsOfImagesThatAreGone() throws Exception {
        StoredBlob blob = store(TestImages.png(TestImages.halves(600, 600, false)));
        Path thumb = thumbnails.get(owner, blob.hash(), "image/png", blob.size()).orElseThrow().path();

        assertThat(thumbnails.sweep((o, h) -> true)).isZero();
        assertThat(thumbnails.sweep((o, h) -> false)).isEqualTo(1);
        assertThat(thumb).doesNotExist();
    }

    @Test
    void filesThatCantBeReadHaveNoneAndArentTriedAgain() throws Exception {
        StoredBlob notAnImage = store("just text".getBytes(StandardCharsets.UTF_8));
        // Claims to be an image but isn't one.
        assertThat(thumbnails.get(owner, notAnImage.hash(), "image/png", notAnImage.size())).isEmpty();
        Path marker = dir.resolve("thumbs").resolve(owner.toString()).resolve(notAnImage.hash() + ".none");
        assertThat(marker).exists();
        assertThat(thumbnails.get(owner, notAnImage.hash(), "image/png", notAnImage.size())).isEmpty();

        assertThat(thumbnails.get(owner, notAnImage.hash(), "application/zip", notAnImage.size())).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "image/jpeg, 1000, true",
            "IMAGE/PNG, 1000, true",
            "image/webp, 1000, true",
            "image/svg+xml, 1000, false",
            "application/pdf, 1000, true",
            "text/markdown, 1000, true",
            "text/plain; charset=UTF-8, 100000000, true",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document, 1000, true",
            "application/vnd.oasis.opendocument.spreadsheet, 1000, true",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, 1000, false",
            "application/zip, 1000, false",
            "video/mp4, 1000, false",
            "image/jpeg, 0, false",
            "image/jpeg, 100000000, false"
    })
    void decidesWhichFilesGetOne(String type, long size, boolean expected) {
        assertThat(ThumbnailService.canMake(type, size)).isEqualTo(expected);
    }

    @Test
    void readsOrientationFromExifAndIgnoresEverythingElse() {
        byte[] sideways = TestImages.jpegWithOrientation(TestImages.halves(10, 10, false), 8);
        assertThat(ExifOrientation.read(new ByteArrayInputStream(sideways))).isEqualTo(8);
        assertThat(ExifOrientation.read(new ByteArrayInputStream(TestImages.jpeg(TestImages.halves(10, 10, false)))))
                .isEqualTo(1);
        assertThat(ExifOrientation.read(new ByteArrayInputStream(TestImages.png(TestImages.halves(10, 10, false)))))
                .isEqualTo(1);
        assertThat(ExifOrientation.read(new ByteArrayInputStream(new byte[] {(byte) 0xFF, (byte) 0xD8, 1})))
                .isEqualTo(1);
    }

    private StoredBlob store(byte[] bytes) {
        return blobs.store(owner, new ByteArrayInputStream(bytes));
    }

    private static boolean isMostly(int argb, Color color) {
        Color c = new Color(argb);
        int distance = Math.abs(c.getRed() - color.getRed()) + Math.abs(c.getGreen() - color.getGreen())
                + Math.abs(c.getBlue() - color.getBlue());
        return distance < 90;
    }
}
