package com.andrija.homesiloserver.thumbnail;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Reads the EXIF orientation of a JPEG. Phones store photos as the sensor saw them
 * and record how to turn them upright in this tag; ignoring it gives sideways
 * thumbnails. Returns 1 (as stored) when there is no tag or the data is unusual.
 */
final class ExifOrientation {

    static final int NORMAL = 1;
    private static final int ORIENTATION_TAG = 0x0112;

    private ExifOrientation() {
    }

    static int read(InputStream input) {
        try {
            DataInputStream in = new DataInputStream(input);
            if (in.readUnsignedShort() != 0xFFD8) return NORMAL; // not a JPEG
            while (true) {
                int marker = in.readUnsignedShort();
                if ((marker & 0xFF00) != 0xFF00 || marker == 0xFFDA || marker == 0xFFD9) return NORMAL;
                int length = in.readUnsignedShort() - 2;
                if (length < 0) return NORMAL;
                if (marker == 0xFFE1) {
                    byte[] segment = in.readNBytes(length);
                    int orientation = fromApp1(segment);
                    if (orientation != 0) return orientation;
                } else {
                    in.skipNBytes(length);
                }
            }
        } catch (EOFException ex) {
            return NORMAL;
        } catch (IOException | RuntimeException ex) {
            return NORMAL;
        }
    }

    /** The orientation in an APP1 segment, or 0 if it isn't EXIF or has none. */
    static int fromApp1(byte[] segment) {
        if (segment.length < 14 || segment[0] != 'E' || segment[1] != 'x' || segment[2] != 'i' || segment[3] != 'f') {
            return 0;
        }
        ByteBuffer tiff = ByteBuffer.wrap(segment, 6, segment.length - 6).slice();
        if (tiff.getShort(0) == 0x4949) tiff.order(ByteOrder.LITTLE_ENDIAN); // "II"
        else if (tiff.getShort(0) == 0x4D4D) tiff.order(ByteOrder.BIG_ENDIAN); // "MM"
        else return 0;

        int ifd = tiff.getInt(4);
        if (ifd < 8 || ifd + 2 > tiff.limit()) return 0;
        int entries = Short.toUnsignedInt(tiff.getShort(ifd));
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > tiff.limit()) return 0;
            if (Short.toUnsignedInt(tiff.getShort(entry)) == ORIENTATION_TAG) {
                int value = Short.toUnsignedInt(tiff.getShort(entry + 8));
                return value >= 1 && value <= 8 ? value : 0;
            }
        }
        return 0;
    }
}
