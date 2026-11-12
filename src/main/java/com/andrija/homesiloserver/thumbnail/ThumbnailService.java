package com.andrija.homesiloserver.thumbnail;

import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.storage.BlobStorage;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

/**
 * Small previews of images for the grid view. They are a cache next to the stored
 * files: made from a blob on first request (or right after upload), kept on disk by
 * owner and content hash, never counted against anyone's quota, and safe to delete.
 *
 * <p>Large photos are decoded with subsampling so memory stays low, phone photos are
 * turned upright from their EXIF orientation, and images with transparency stay PNG.
 */
@Slf4j
@Service
public class ThumbnailService {

    /** Longest edge in pixels: sharp for a ~240px tile on a 2x screen. */
    public static final int SIZE = 480;
    /** Bigger files aren't decoded just for a preview. */
    static final long MAX_SOURCE_BYTES = 64L * 1024 * 1024;
    /** Guards against decompression bombs (tiny files that claim huge dimensions). */
    static final long MAX_SOURCE_PIXELS = 150_000_000L;
    private static final float JPEG_QUALITY = 0.82f;
    private static final Set<String> TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp");
    private static final Pattern HASH = Pattern.compile("^[0-9a-f]{64}$");

    private final BlobStorage blobStorage;
    private final Path root;
    /** One generation per image at a time; concurrent requests share the result. */
    private final ConcurrentHashMap<String, CompletableFuture<Optional<Thumbnail>>> inFlight = new ConcurrentHashMap<>();
    /** Pre-makes thumbnails after uploads, off the request thread. Two threads: it's CPU work. */
    private final ExecutorService warmer = Executors.newFixedThreadPool(2,
            Thread.ofPlatform().name("thumbnails-", 0).daemon(true).factory());

    public ThumbnailService(BlobStorage blobStorage, HomesiloProperties properties) {
        this.blobStorage = blobStorage;
        this.root = Paths.get(properties.storage().thumbnailPath()).toAbsolutePath().normalize();
        ImageIO.scanForPlugins(); // pick up the TwelveMonkeys readers inside the packaged jar
        ImageIO.setUseCache(false); // decode in memory rather than via temp files
    }

    public record Thumbnail(Path path, MediaType type) {}

    /** Whether HomeSilo makes previews for this file. The client uses it to decide what to ask for. */
    public static boolean canMake(String contentType, long size) {
        return contentType != null && size > 0 && size <= MAX_SOURCE_BYTES
                && TYPES.contains(contentType.toLowerCase(Locale.ROOT));
    }

    /** The cached thumbnail, made now if needed; empty if the image can't be read. */
    public Optional<Thumbnail> get(UUID ownerId, String hash, String contentType, long size) {
        if (!canMake(contentType, size) || !HASH.matcher(hash).matches()) return Optional.empty();
        Optional<Thumbnail> cached = cached(ownerId, hash);
        if (cached.isPresent()) return cached;

        String key = ownerId + "/" + hash;
        CompletableFuture<Optional<Thumbnail>> mine = new CompletableFuture<>();
        CompletableFuture<Optional<Thumbnail>> running = inFlight.putIfAbsent(key, mine);
        if (running != null) return running.join();
        try {
            Optional<Thumbnail> made = generate(ownerId, hash);
            mine.complete(made);
            return made;
        } catch (RuntimeException ex) {
            mine.complete(Optional.empty());
            throw ex;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    /** After an upload commits, make its thumbnail in the background so the grid is instant. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUploaded(ThumbnailWanted event) {
        if (!canMake(event.contentType(), event.size())) return;
        try {
            warmer.execute(() -> {
                try {
                    get(event.ownerId(), event.hash(), event.contentType(), event.size());
                } catch (RuntimeException ex) {
                    log.debug("Couldn't pre-make thumbnail for {}: {}", event.hash(), ex.getMessage());
                }
            });
        } catch (RejectedExecutionException ex) {
            // Shutting down; it will be made on first request instead.
        }
    }

    /** Drops the thumbnails of a blob that was deleted. */
    public void evict(UUID ownerId, String hash) {
        if (!HASH.matcher(hash).matches()) return;
        Path dir = root.resolve(ownerId.toString());
        deleteQuietly(dir.resolve(hash + ".jpg"));
        deleteQuietly(dir.resolve(hash + ".png"));
    }

    /** Removes thumbnails whose image no longer exists (nightly). Returns how many were removed. */
    public int sweep(BiPredicate<UUID, String> isReferenced) {
        if (!Files.isDirectory(root)) return 0;
        int removed = 0;
        try (DirectoryStream<Path> owners = Files.newDirectoryStream(root, Files::isDirectory)) {
            for (Path ownerDir : owners) {
                UUID owner = parseUuid(ownerDir.getFileName().toString());
                if (owner == null) continue;
                try (DirectoryStream<Path> files = Files.newDirectoryStream(ownerDir)) {
                    for (Path file : files) {
                        String name = file.getFileName().toString();
                        int dot = name.lastIndexOf('.');
                        String hash = dot > 0 ? name.substring(0, dot) : name;
                        boolean stale = !HASH.matcher(hash).matches() || !isReferenced.test(owner, hash);
                        if (stale && Files.deleteIfExists(file)) removed++;
                    }
                }
            }
        } catch (IOException ex) {
            log.warn("Thumbnail sweep stopped early: {}", ex.getMessage());
        }
        return removed;
    }

    @PreDestroy
    void shutdown() {
        warmer.shutdownNow();
    }

    // ── Making one ───────────────────────────────────────────────────────────

    private Optional<Thumbnail> cached(UUID ownerId, String hash) {
        Path dir = root.resolve(ownerId.toString());
        Path jpg = dir.resolve(hash + ".jpg");
        if (Files.isRegularFile(jpg)) return Optional.of(new Thumbnail(jpg, MediaType.IMAGE_JPEG));
        Path png = dir.resolve(hash + ".png");
        if (Files.isRegularFile(png)) return Optional.of(new Thumbnail(png, MediaType.IMAGE_PNG));
        return Optional.empty();
    }

    private Optional<Thumbnail> generate(UUID ownerId, String hash) {
        BufferedImage source;
        int orientation;
        try {
            source = decode(ownerId, hash);
            if (source == null) return Optional.empty();
            try (InputStream in = blobStorage.load(ownerId, hash).getInputStream()) {
                orientation = ExifOrientation.read(in);
            }
        } catch (IOException | RuntimeException ex) {
            log.debug("Can't make a thumbnail of {}: {}", hash, ex.getMessage());
            return Optional.empty();
        }

        BufferedImage thumbnail = orient(scale(source), orientation);
        boolean transparent = thumbnail.getColorModel().hasAlpha();
        Path dir = root.resolve(ownerId.toString());
        Path target = dir.resolve(hash + (transparent ? ".png" : ".jpg"));
        try {
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, ".thumb-", ".tmp");
            try {
                if (transparent) {
                    ImageIO.write(thumbnail, "png", tmp.toFile());
                } else {
                    writeJpeg(withoutAlpha(thumbnail), tmp);
                }
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException ex) {
            log.warn("Couldn't save thumbnail for {}: {}", hash, ex.getMessage());
            return Optional.empty();
        }
        return Optional.of(new Thumbnail(target, transparent ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG));
    }

    /** Decodes the first frame, skipping pixels so the result is about twice the thumbnail size. */
    private BufferedImage decode(UUID ownerId, String hash) throws IOException {
        try (InputStream in = blobStorage.load(ownerId, hash).getInputStream();
             ImageInputStream stream = ImageIO.createImageInputStream(in)) {
            if (stream == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_SOURCE_PIXELS) return null;
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, Math.max(width, height) / (SIZE * 2));
                param.setSourceSubsampling(step, step, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
    }

    /** Halves the image until close to the target, then one last smooth step: sharp without aliasing. */
    static BufferedImage scale(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        double factor = Math.min(1.0, (double) SIZE / Math.max(width, height));
        int targetW = Math.max(1, (int) Math.round(width * factor));
        int targetH = Math.max(1, (int) Math.round(height * factor));
        int type = image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;

        BufferedImage current = image;
        int w = width;
        int h = height;
        do {
            w = Math.max(targetW, w / 2);
            h = Math.max(targetH, h / 2);
            if (w < targetW * 2 && h < targetH * 2) {
                w = targetW;
                h = targetH;
            }
            BufferedImage next = new BufferedImage(w, h, type);
            Graphics2D g = next.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(current, 0, 0, w, h, null);
            g.dispose();
            current = next;
        } while (w != targetW || h != targetH);
        return current;
    }

    /** Turns the image upright according to its EXIF orientation (1–8). */
    static BufferedImage orient(BufferedImage image, int orientation) {
        if (orientation <= 1 || orientation > 8) return image;
        int w = image.getWidth();
        int h = image.getHeight();
        boolean swaps = orientation >= 5;
        // Each transform maps a source pixel (x, y) to where it belongs once upright.
        AffineTransform t = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);   // mirrored
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);  // upside down
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);   // mirrored, upside down
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);    // mirrored, on its side
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);   // turn 90° clockwise
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);  // mirrored, on its other side
            default -> new AffineTransform(0, -1, 1, 0, 0, w);  // 8: turn 90° counter-clockwise
        };
        int type = image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage upright = new BufferedImage(swaps ? h : w, swaps ? w : h, type);
        Graphics2D g = upright.createGraphics();
        g.drawImage(image, t, null);
        g.dispose();
        return upright;
    }

    /** JPEG can't hold transparency; images without any get flattened on white. */
    private static BufferedImage withoutAlpha(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) return image;
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static void writeJpeg(BufferedImage image, Path target) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.debug("Couldn't delete {}: {}", path, ex.getMessage());
        }
    }
}
