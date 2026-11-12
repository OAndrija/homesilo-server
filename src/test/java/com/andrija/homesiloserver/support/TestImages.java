package com.andrija.homesiloserver.support;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

/** Real image files for tests, built in memory. */
public final class TestImages {

    private TestImages() {
    }

    /** A two-tone image (left half red, right half blue) so orientation is visible in pixels. */
    public static BufferedImage halves(int width, int height, boolean transparent) {
        BufferedImage image = new BufferedImage(width, height,
                transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        if (transparent) {
            g.setComposite(java.awt.AlphaComposite.Clear);
            g.fillRect(0, 0, width, height);
            g.setComposite(java.awt.AlphaComposite.SrcOver);
        }
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 2, transparent ? height / 2 : height);
        g.setColor(Color.BLUE);
        g.fillRect(width / 2, 0, width - width / 2, transparent ? height / 2 : height);
        g.dispose();
        return image;
    }

    public static byte[] png(BufferedImage image) {
        return encode(image, "png");
    }

    public static byte[] jpeg(BufferedImage image) {
        return encode(image, "jpeg");
    }

    /** A JPEG carrying an EXIF orientation tag, like photos from a phone held sideways. */
    public static byte[] jpegWithOrientation(BufferedImage image, int orientation) {
        byte[] jpeg = jpeg(image);
        ByteBuffer app1 = ByteBuffer.allocate(36);
        app1.putShort((short) 0xFFE1).putShort((short) 34);
        app1.put(new byte[] {'E', 'x', 'i', 'f', 0, 0});
        app1.put(new byte[] {'M', 'M'}).putShort((short) 0x2A).putInt(8); // big-endian TIFF, IFD0 at 8
        app1.putShort((short) 1); // one entry
        app1.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        app1.putInt(0); // no next IFD
        byte[] out = new byte[jpeg.length + app1.capacity()];
        System.arraycopy(jpeg, 0, out, 0, 2); // SOI
        System.arraycopy(app1.array(), 0, out, 2, app1.capacity());
        System.arraycopy(jpeg, 2, out, 2 + app1.capacity(), jpeg.length - 2);
        return out;
    }

    private static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
