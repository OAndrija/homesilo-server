package com.andrija.homesiloserver.thumbnail;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** The first page of a PDF. Password-protected PDFs have no preview. */
final class PdfRenderer implements ThumbnailRenderer {

    static final String TYPE = "application/pdf";

    @Override
    public boolean supports(String contentType) {
        return TYPE.equals(contentType);
    }

    @Override
    public BufferedImage render(Source source, String contentType) throws IOException {
        try (InputStream in = source.open(); PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(in))) {
            if (document.getNumberOfPages() == 0) return null;
            PDPage page = document.getPage(0);
            PDRectangle box = page.getCropBox();
            float longest = Math.max(box.getWidth(), box.getHeight());
            if (longest <= 0) return null;
            // Render at twice the thumbnail size (1.0 = 72 dpi); the final shrink keeps text crisp.
            float scale = Math.min(6f, ThumbnailService.SIZE * 2f / longest);
            PDFRenderer renderer = new PDFRenderer(document);
            renderer.setSubsamplingAllowed(true);
            return renderer.renderImage(0, scale, ImageType.RGB);
        } catch (InvalidPasswordException ex) {
            return null;
        }
    }
}
