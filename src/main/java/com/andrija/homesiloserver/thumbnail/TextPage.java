package com.andrija.homesiloserver.thumbnail;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws the start of a document as a small white page, the way file managers show
 * documents they can't render exactly: real text, real headings, at page size.
 */
final class TextPage {

    /** Portrait, close to A4. */
    static final int WIDTH = 340;
    static final int HEIGHT = 480;
    private static final int MARGIN = 26;
    private static final Color PAPER = Color.WHITE;
    private static final Color INK = new Color(0x2B, 0x2F, 0x36);
    private static final Color HEADING_INK = new Color(0x11, 0x14, 0x1A);

    /** A paragraph of the document; headings are drawn larger and bold. */
    record Block(String text, boolean heading) {}

    enum Style { PROSE, CODE }

    private TextPage() {
    }

    static BufferedImage draw(List<Block> blocks, Style style) {
        BufferedImage page = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = page.createGraphics();
        try {
            g.setColor(PAPER);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            Font body = style == Style.CODE ? new Font(Font.MONOSPACED, Font.PLAIN, 11) : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            Font heading = new Font(Font.SANS_SERIF, Font.BOLD, 17);
            int y = MARGIN;
            int width = WIDTH - 2 * MARGIN;

            for (Block block : blocks) {
                Font font = block.heading() ? heading : body;
                g.setFont(font);
                g.setColor(block.heading() ? HEADING_INK : INK);
                FontMetrics metrics = g.getFontMetrics();
                int lineHeight = Math.round(metrics.getHeight() * (style == Style.CODE ? 1.15f : 1.3f));
                if (block.heading() && y > MARGIN) y += lineHeight / 3;

                List<String> lines = style == Style.CODE
                        ? List.of(clipToWidth(block.text(), metrics, width))
                        : wrap(block.text(), metrics, width);
                for (String line : lines) {
                    if (y + metrics.getAscent() > HEIGHT - MARGIN / 2) return page;
                    g.drawString(line, MARGIN, y + metrics.getAscent());
                    y += lineHeight;
                }
                if (style == Style.PROSE) y += lineHeight / 3; // space between paragraphs
            }
            return page;
        } finally {
            g.dispose();
        }
    }

    /** Word-wraps a paragraph; a word longer than the line is cut. */
    static List<String> wrap(String text, FontMetrics metrics, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (metrics.stringWidth(candidate) <= width) {
                line.setLength(0);
                line.append(candidate);
            } else {
                if (!line.isEmpty()) lines.add(line.toString());
                line.setLength(0);
                line.append(clipToWidth(word, metrics, width));
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private static String clipToWidth(String text, FontMetrics metrics, int width) {
        String expanded = text.replace("\t", "    ");
        if (metrics.stringWidth(expanded) <= width) return expanded;
        int end = expanded.length();
        while (end > 0 && metrics.stringWidth(expanded.substring(0, end)) > width) end--;
        return expanded.substring(0, end);
    }
}
