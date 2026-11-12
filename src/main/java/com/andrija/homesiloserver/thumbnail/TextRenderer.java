package com.andrija.homesiloserver.thumbnail;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Notes, Markdown, code and data files: the first lines on a page. */
final class TextRenderer implements ThumbnailRenderer {

    static final Set<String> TEXT_LIKE = Set.of(
            "application/json", "application/xml", "application/javascript", "application/x-sh",
            "application/x-yaml", "application/yaml");
    /** More than fills a page. */
    private static final int READ_BYTES = 8 * 1024;
    private static final int MAX_LINES = 60;

    @Override
    public boolean supports(String contentType) {
        return (contentType.startsWith("text/") || TEXT_LIKE.contains(contentType)) && !contentType.equals("text/html");
    }

    @Override
    public BufferedImage render(Source source, String contentType) throws IOException {
        byte[] head;
        try (InputStream in = source.open()) {
            head = in.readNBytes(READ_BYTES);
        }
        String text = decode(head);
        if (text == null || text.isBlank()) return null;

        boolean markdown = contentType.contains("markdown");
        boolean prose = markdown || contentType.equals("text/plain");
        List<TextPage.Block> blocks = new ArrayList<>();
        for (String raw : text.split("\\R", -1)) {
            if (blocks.size() >= MAX_LINES) break;
            String line = raw.stripTrailing();
            if (prose && line.isBlank()) continue;
            if (markdown && line.startsWith("#")) {
                blocks.add(new TextPage.Block(line.replaceFirst("^#+\\s*", ""), true));
            } else if (markdown) {
                blocks.add(new TextPage.Block(line.replaceFirst("^\\s*[-*+]\\s+", "• "), false));
            } else {
                blocks.add(new TextPage.Block(line, false));
            }
        }
        return TextPage.draw(blocks, prose ? TextPage.Style.PROSE : TextPage.Style.CODE);
    }

    /** UTF-8 if it is valid UTF-8 (ignoring a character cut off at the end), else Latin-1; null for binary. */
    static String decode(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) return null; // binary data, not text
        }
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            // Probably a multi-byte character cut off by the read limit; try without the last few bytes.
            for (int cut = 1; cut <= 3 && cut < bytes.length; cut++) {
                try {
                    return decoder.reset().decode(ByteBuffer.wrap(bytes, 0, bytes.length - cut)).toString();
                } catch (CharacterCodingException ignored) {
                    // keep trying
                }
            }
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }
}
