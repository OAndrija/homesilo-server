package com.andrija.homesiloserver.thumbnail;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** Turns one kind of file into a picture that ThumbnailService shrinks and caches. */
interface ThumbnailRenderer {

    boolean supports(String contentType);

    /**
     * The picture to show for the file, upright, at least as big as a thumbnail;
     * null when this particular file has none (e.g. an encrypted PDF).
     */
    BufferedImage render(Source source, String contentType) throws IOException;

    /** Opens the file's bytes; may be called more than once. */
    @FunctionalInterface
    interface Source {
        InputStream open() throws IOException;
    }
}
