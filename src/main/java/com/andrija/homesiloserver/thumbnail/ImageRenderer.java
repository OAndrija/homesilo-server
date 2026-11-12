package com.andrija.homesiloserver.thumbnail;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Set;

/**
 * Photos and pictures. Large ones are decoded with subsampling so memory stays low,
 * and phone photos are turned upright from their EXIF orientation.
 */
final class ImageRenderer implements ThumbnailRenderer {

    static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp");
    /** Guards against decompression bombs (tiny files that claim huge dimensions). */
    static final long MAX_PIXELS = 150_000_000L;

    @Override
    public boolean supports(String contentType) {
        return TYPES.contains(contentType);
    }

    @Override
    public BufferedImage render(Source source, String contentType) throws IOException {
        BufferedImage image = decode(source);
        if (image == null) return null;
        if (!contentType.equals("image/jpeg")) return image;
        try (InputStream in = source.open()) {
            return orient(image, ExifOrientation.read(in));
        }
    }

    /** Decodes the first frame, skipping pixels so the result is about twice the thumbnail size. */
    private static BufferedImage decode(Source source) throws IOException {
        try (InputStream in = source.open(); ImageInputStream stream = ImageIO.createImageInputStream(in)) {
            if (stream == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) return null;
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, Math.max(width, height) / (ThumbnailService.SIZE * 2));
                param.setSourceSubsampling(step, step, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
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
}
