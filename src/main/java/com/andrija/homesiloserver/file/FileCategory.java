package com.andrija.homesiloserver.file;

import java.util.Locale;

/** Coarse buckets for the storage breakdown on the dashboard. */
public enum FileCategory {
    DOCUMENTS("Documents"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    ARCHIVES("Archives"),
    OTHER("Other");

    private final String label;

    FileCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static FileCategory of(String contentType) {
        if (contentType == null) return OTHER;
        String type = contentType.toLowerCase(Locale.ROOT);
        if (type.startsWith("image/")) return IMAGES;
        if (type.startsWith("video/")) return VIDEOS;
        if (type.startsWith("audio/")) return AUDIO;
        if (type.startsWith("text/")
                || type.contains("pdf")
                || type.contains("word")
                || type.contains("document")
                || type.contains("spreadsheet")
                || type.contains("presentation")
                || type.contains("excel")
                || type.contains("powerpoint")) return DOCUMENTS;
        if (type.contains("zip")
                || type.contains("tar")
                || type.contains("gzip")
                || type.contains("7z")
                || type.contains("rar")
                || type.contains("compressed")) return ARCHIVES;
        return OTHER;
    }
}
