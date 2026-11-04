package com.andrija.homesiloserver.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class FileServiceUnitTest {

    @ParameterizedTest
    @CsvSource({
            "report.pdf, report.pdf",
            "../../etc/passwd, passwd",
            "C:\\Users\\me\\photo.jpg, photo.jpg",
            "'  spaced.txt  ', spaced.txt",
            "'..', unnamed",
            "'', unnamed"
    })
    void sanitizesFileNames(String input, String expected) {
        assertThat(FileService.sanitizeFileName(input)).isEqualTo(expected);
    }

    @Test
    void truncatesVeryLongNamesButKeepsTheExtension() {
        String name = FileService.sanitizeFileName("a".repeat(300) + ".txt");
        assertThat(name).hasSize(255).endsWith(".txt");
    }

    @Test
    void escapesLikeWildcards() {
        assertThat(FileService.likePattern("50%_off\\")).isEqualTo("%50\\%\\_off\\\\%");
    }

    @Test
    void categorisesContentTypes() {
        assertThat(FileCategory.of("image/png")).isEqualTo(FileCategory.IMAGES);
        assertThat(FileCategory.of("audio/flac")).isEqualTo(FileCategory.AUDIO);
        assertThat(FileCategory.of("application/pdf")).isEqualTo(FileCategory.DOCUMENTS);
        assertThat(FileCategory.of("application/zip")).isEqualTo(FileCategory.ARCHIVES);
        assertThat(FileCategory.of("application/octet-stream")).isEqualTo(FileCategory.OTHER);
        assertThat(FileCategory.of(null)).isEqualTo(FileCategory.OTHER);
    }
}
