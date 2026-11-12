package com.andrija.homesiloserver.thumbnail;

import javax.imageio.ImageIO;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Word and LibreOffice documents. LibreOffice files carry a preview of their first
 * page, which is used as is. Word files usually don't, so their opening text and
 * headings are drawn on a page instead (laying them out exactly would need an
 * office suite on the server).
 */
final class DocumentRenderer implements ThumbnailRenderer {

    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    static final String ODT = "application/vnd.oasis.opendocument.text";
    static final String ODS = "application/vnd.oasis.opendocument.spreadsheet";
    static final String ODP = "application/vnd.oasis.opendocument.presentation";
    private static final Map<String, String> TEXT_PART = Map.of(DOCX, "word/document.xml", ODT, "content.xml");
    private static final List<String> EMBEDDED_PREVIEWS = List.of(
            "Thumbnails/thumbnail.png", "docProps/thumbnail.jpeg", "docProps/thumbnail.png");
    /** Zip bombs: never read more than this from one entry. */
    private static final long MAX_ENTRY_BYTES = 16L * 1024 * 1024;
    private static final int MAX_BLOCKS = 40;

    @Override
    public boolean supports(String contentType) {
        return contentType.equals(DOCX) || contentType.equals(ODT) || contentType.equals(ODS) || contentType.equals(ODP);
    }

    @Override
    public BufferedImage render(Source source, String contentType) throws IOException {
        List<TextPage.Block> blocks = null;
        String textPart = TEXT_PART.get(contentType);
        try (ZipInputStream zip = new ZipInputStream(source.open())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (EMBEDDED_PREVIEWS.contains(name)) {
                    byte[] bytes = limited(zip).readAllBytes();
                    BufferedImage preview = ImageIO.read(new ByteArrayInputStream(bytes));
                    if (preview != null) return preview;
                } else if (name.equals(textPart)) {
                    blocks = paragraphs(limited(zip), contentType.equals(DOCX));
                }
            }
        }
        return blocks == null || blocks.isEmpty() ? null : TextPage.draw(blocks, TextPage.Style.PROSE);
    }

    /**
     * The first paragraphs of document.xml (Word) or content.xml (LibreOffice), with
     * headings marked. Streamed, with DTDs and external entities switched off.
     */
    static List<TextPage.Block> paragraphs(InputStream xml, boolean word) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        List<TextPage.Block> blocks = new ArrayList<>();
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(xml);
            StringBuilder text = null;
            boolean heading = false;
            boolean inText = false;
            while (reader.hasNext() && blocks.size() < MAX_BLOCKS) {
                int event = reader.next();
                String local = event == XMLStreamConstants.START_ELEMENT || event == XMLStreamConstants.END_ELEMENT
                        ? reader.getLocalName() : "";
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if (word ? local.equals("p") : (local.equals("p") || local.equals("h"))) {
                        text = new StringBuilder();
                        heading = !word && local.equals("h");
                    } else if (text != null && word && local.equals("pStyle")) {
                        String style = reader.getAttributeValue(null, "val");
                        if (style == null) style = attributeEndingWith(reader, "val");
                        heading = style != null && (style.startsWith("Heading") || style.equals("Title"));
                    } else if (text != null && (local.equals("tab") || local.equals("br") || local.equals("s")
                            || local.equals("line-break"))) {
                        text.append(' ');
                    }
                    inText = text != null && (!word || local.equals("t"));
                } else if (event == XMLStreamConstants.CHARACTERS && text != null && (inText || !word)) {
                    text.append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (word && local.equals("t")) inText = false;
                    if (text != null && (local.equals("p") || (!word && local.equals("h")))) {
                        String paragraph = text.toString().strip();
                        if (!paragraph.isEmpty()) blocks.add(new TextPage.Block(paragraph, heading));
                        text = null;
                        heading = false;
                    }
                }
            }
            reader.close();
        } catch (XMLStreamException ex) {
            // Damaged document: keep whatever was read before the problem.
        }
        return blocks;
    }

    private static String attributeEndingWith(XMLStreamReader reader, String name) {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if (reader.getAttributeLocalName(i).equals(name)) return reader.getAttributeValue(i);
        }
        return null;
    }

    /** Reads at most MAX_ENTRY_BYTES of the current entry and never closes the zip. */
    private static InputStream limited(InputStream in) {
        return new FilterInputStream(in) {
            private long remaining = MAX_ENTRY_BYTES;

            @Override
            public int read() throws IOException {
                if (remaining <= 0) return -1;
                int b = super.read();
                if (b >= 0) remaining--;
                return b;
            }

            @Override
            public int read(byte[] buffer, int off, int len) throws IOException {
                if (remaining <= 0) return -1;
                int n = super.read(buffer, off, (int) Math.min(len, remaining));
                if (n > 0) remaining -= n;
                return n;
            }

            @Override
            public void close() {
                // the zip stream is closed by its owner
            }
        };
    }
}
