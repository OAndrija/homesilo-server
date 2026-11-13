package com.andrija.homesiloserver.thumbnail;

import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.storage.BlobStorage.StoredBlob;
import com.andrija.homesiloserver.storage.LocalBlobStorage;
import com.andrija.homesiloserver.support.TestImages;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentThumbnailsTest {

    @TempDir
    Path dir;

    private LocalBlobStorage blobs;
    private ThumbnailService thumbnails;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        HomesiloProperties properties = new HomesiloProperties(
                new HomesiloProperties.Jwt("x".repeat(32), Duration.ofHours(1)),
                new HomesiloProperties.Auth("s", false, true, 5, Duration.ofMinutes(1), ""),
                new HomesiloProperties.Storage(dir.resolve("blobs").toString(), DataSize.ofGigabytes(1),
                        dir.resolve("thumbs").toString()),
                new HomesiloProperties.Trash(30, "0 0 3 * * *"),
                new HomesiloProperties.Cors(List.of()),
                new HomesiloProperties.Activity(90));
        blobs = new LocalBlobStorage(properties);
        blobs.init();
        thumbnails = new ThumbnailService(blobs, properties);
    }

    @Test
    void pdfsShowTheirFirstPage() throws Exception {
        BufferedImage page = render(pdf(false), "application/pdf");

        // US Letter is 612 × 792 points: portrait, scaled to 480 high.
        assertThat(page.getHeight()).isEqualTo(ThumbnailService.SIZE);
        assertThat(page.getWidth()).isBetween(365, 375);
        // The page's red heading bar is there, on white paper.
        assertThat(isMostly(page.getRGB(page.getWidth() / 2, 15), Color.RED)).isTrue();
        assertThat(isMostly(page.getRGB(page.getWidth() / 2, 400), Color.WHITE)).isTrue();
    }

    @Test
    void passwordProtectedPdfsHaveNone() throws Exception {
        StoredBlob blob = store(pdf(true));
        assertThat(thumbnails.get(owner, blob.hash(), "application/pdf", blob.size())).isEmpty();
    }

    @Test
    void wordDocumentsShowTheirOpeningTextOnAPage() throws Exception {
        String xml = """
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
                  <w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr><w:r><w:t>Quarterly report</w:t></w:r></w:p>
                  <w:p><w:r><w:t xml:space="preserve">Sales grew </w:t></w:r><w:r><w:t>in every region.</w:t></w:r></w:p>
                </w:body></w:document>""";
        BufferedImage page = render(zip("word/document.xml", xml.getBytes(StandardCharsets.UTF_8)),
                DocumentRenderer.DOCX);

        assertThat(page.getWidth()).isEqualTo(TextPage.WIDTH);
        assertThat(page.getHeight()).isEqualTo(TextPage.HEIGHT);
        assertThat(hasInk(page, 0, 70)).as("the title is drawn near the top").isTrue();
        assertThat(hasInk(page, 300, 480)).as("the rest of the page is empty").isFalse();
    }

    @Test
    void readsParagraphsAndHeadingsFromWordXml() throws Exception {
        String xml = """
                <w:document xmlns:w="w"><w:body>
                  <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Intro</w:t></w:r></w:p>
                  <w:p><w:r><w:t>One</w:t></w:r><w:r><w:tab/><w:t>two</w:t></w:r></w:p>
                  <w:p></w:p>
                </w:body></w:document>""";
        var blocks = DocumentRenderer.paragraphs(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), true);
        assertThat(blocks).containsExactly(new TextPage.Block("Intro", true), new TextPage.Block("One two", false));
    }

    @Test
    void documentsWithTheirOwnPreviewUseIt() throws Exception {
        byte[] preview = TestImages.png(TestImages.halves(256, 362, false));
        BufferedImage page = render(zip("Thumbnails/thumbnail.png", preview), DocumentRenderer.ODS);
        assertThat(page.getWidth()).isEqualTo(256);
        assertThat(page.getHeight()).isEqualTo(362);
    }

    @Test
    void notesAndCodeShowTheirFirstLines() throws Exception {
        byte[] markdown = "# Trip notes\n\n- Book the tram tour\n- Pack the charger\n".getBytes(StandardCharsets.UTF_8);
        BufferedImage page = render(markdown, "text/markdown");
        assertThat(page.getWidth()).isEqualTo(TextPage.WIDTH);
        assertThat(hasInk(page, 0, 60)).isTrue();

        byte[] json = "{\n  \"name\": \"homesilo\"\n}\n".getBytes(StandardCharsets.UTF_8);
        assertThat(render(json, "application/json").getHeight()).isEqualTo(TextPage.HEIGHT);
    }

    @Test
    void binaryDataPretendingToBeTextHasNone() {
        StoredBlob blob = store(new byte[] {1, 0, 2, 0, 3});
        assertThat(thumbnails.get(owner, blob.hash(), "text/plain", blob.size())).isEmpty();
    }

    @Test
    void decodesTextCutOffInTheMiddleOfACharacter() {
        byte[] cut = "Čeprav".getBytes(StandardCharsets.UTF_8);
        byte[] truncated = java.util.Arrays.copyOf(cut, 1); // half of "Č"
        assertThat(TextRenderer.decode(java.util.Arrays.copyOf(cut, cut.length))).isEqualTo("Čeprav");
        assertThat(TextRenderer.decode(truncated)).isNotNull();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private BufferedImage render(byte[] bytes, String type) throws IOException {
        StoredBlob blob = store(bytes);
        var thumb = thumbnails.get(owner, blob.hash(), type, blob.size()).orElseThrow();
        return ImageIO.read(thumb.path().toFile());
    }

    private StoredBlob store(byte[] bytes) {
        return blobs.store(owner, new ByteArrayInputStream(bytes));
    }

    /** A one-page Letter PDF with a red bar across the top, optionally password protected. */
    private static byte[] pdf(boolean encrypted) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setNonStrokingColor(Color.RED);
                content.addRect(0, 742, 612, 50);
                content.fill();
            }
            if (encrypted) {
                StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", "user-secret",
                        new AccessPermission());
                policy.setEncryptionKeyLength(128);
                document.protect(policy);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] zip(String entry, byte[] content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("mimetype"));
            zip.write("application/zip".getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(content);
        }
        return out.toByteArray();
    }

    /** Whether any row between top and bottom has a clearly non-white pixel. */
    private static boolean hasInk(BufferedImage image, int top, int bottom) {
        for (int y = top; y < Math.min(bottom, image.getHeight()); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color c = new Color(image.getRGB(x, y));
                if (c.getRed() + c.getGreen() + c.getBlue() < 600) return true;
            }
        }
        return false;
    }

    private static boolean isMostly(int argb, Color color) {
        Color c = new Color(argb);
        return Math.abs(c.getRed() - color.getRed()) + Math.abs(c.getGreen() - color.getGreen())
                + Math.abs(c.getBlue() - color.getBlue()) < 90;
    }
}
