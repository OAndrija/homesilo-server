package com.andrija.homesiloserver.file;

import com.andrija.homesiloserver.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileFlowIntegrationTest extends IntegrationTest {

    @Test
    void uploadThenDownloadReturnsTheSameBytesWithDetectedType() throws Exception {
        Session session = register();
        String id = upload(session, "notes.txt", "hello homesilo", null);

        mvc.perform(get("/api/v1/files/{id}/metadata", id).cookie(session.cookie()))
                .andExpect(jsonPath("$.originalFileName").value("notes.txt"))
                .andExpect(jsonPath("$.contentType").value("text/plain"))
                .andExpect(jsonPath("$.size").value(14));

        mvc.perform(get("/api/v1/files/{id}/download", id).cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(content().string("hello homesilo"));
    }

    @Test
    void clientSuppliedPathsAreStrippedFromFileNames() throws Exception {
        Session session = register();
        String id = upload(session, "../../etc/passwd", "x", null);
        mvc.perform(get("/api/v1/files/{id}/metadata", id).cookie(session.cookie()))
                .andExpect(jsonPath("$.originalFileName").value("passwd"));
    }

    @Test
    void sameContentCanLiveInTwoFoldersAndRetryingAnUploadIsIdempotent() throws Exception {
        Session session = register();
        String folder = createFolder(session, "Backups", null);

        String first = upload(session, "photo.jpg", "same-bytes", null);
        String second = upload(session, "photo.jpg", "same-bytes", folder);
        String retry = upload(session, "photo.jpg", "same-bytes", folder);

        assertThat(second).isNotEqualTo(first);
        assertThat(retry).isEqualTo(second);

        // Deleting one entry must not break the other, which shares the blob.
        trashFile(session, first);
        mvc.perform(delete("/api/v1/files/{id}", first).cookie(session.cookie()).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/files/{id}/download", second).cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(content().string("same-bytes"));
    }

    @Test
    void uploadsBeyondTheQuotaAreRejected() throws Exception {
        Session session = register();
        setQuota(session, 10);
        mvc.perform(uploadRequest(session, "big.bin", "more than ten bytes", null))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.detail").value(containsString("Not enough storage")));
    }

    @Test
    void uploadingIntoATrashedFolderIsRejected() throws Exception {
        Session session = register();
        String folder = createFolder(session, "Old", null);
        trashFolder(session, folder);
        mvc.perform(uploadRequest(session, "a.txt", "a", folder)).andExpect(status().isConflict());
    }

    @Test
    void usersCannotSeeEachOthersFiles() throws Exception {
        Session alice = register();
        Session bob = register();
        String id = upload(alice, "private.txt", "secret", null);

        mvc.perform(get("/api/v1/files/{id}/download", id).cookie(bob.cookie())).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/files/{id}/trash", id).cookie(bob.cookie()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void htmlPreviewIsServedAsSandboxedPlainText() throws Exception {
        Session session = register();
        String id = upload(session, "evil.html", "<html><script>alert(1)</script></html>", null);

        mvc.perform(get("/api/v1/files/{id}/preview", id).cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/plain")))
                .andExpect(header().string("Content-Security-Policy", containsString("sandbox")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")));
    }

    @Test
    void renameMoveStarTrashRestoreLifecycle() throws Exception {
        Session session = register();
        String folder = createFolder(session, "Docs", null);
        String id = upload(session, "draft.txt", "v1", null);

        mvc.perform(patch("/api/v1/files/{id}/rename", id).cookie(session.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("name", "final.txt")))
                .andExpect(jsonPath("$.originalFileName").value("final.txt"));

        mvc.perform(patch("/api/v1/files/{id}/rename", id).cookie(session.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("name", "a/b.txt")))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/v1/files/{id}/move", id).cookie(session.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("targetFolderId", folder)))
                .andExpect(jsonPath("$.folderId").value(folder))
                .andExpect(jsonPath("$.folderName").value("Docs"));

        mvc.perform(patch("/api/v1/files/{id}/star", id).cookie(session.cookie()).with(csrf()))
                .andExpect(jsonPath("$.starred").value(true));

        trashFile(session, id);
        mvc.perform(get("/api/v1/files/{id}/download", id).cookie(session.cookie())).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/files/trash").cookie(session.cookie()))
                .andExpect(jsonPath("$.content[0].id").value(id));

        mvc.perform(patch("/api/v1/files/{id}/restore", id).cookie(session.cookie()).with(csrf()))
                .andExpect(jsonPath("$.trashed").value(false));
    }

    @Test
    void deletingRequiresTheFileToBeTrashedFirst() throws Exception {
        Session session = register();
        String id = upload(session, "keep.txt", "keep", null);
        mvc.perform(delete("/api/v1/files/{id}", id).cookie(session.cookie()).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void searchEscapesLikeWildcards() throws Exception {
        Session session = register();
        String percent = upload(session, "100% done.txt", "a", null);
        String other = upload(session, "1000 done.txt", "b", null);
        trashFile(session, percent);
        trashFile(session, other);

        mvc.perform(get("/api/v1/files/trash/search").param("query", "%").cookie(session.cookie()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(percent));
    }

    @Test
    void invalidSortPropertyIsABadRequestNotAServerError() throws Exception {
        Session session = register();
        mvc.perform(get("/api/v1/files").param("sort", "nope,desc").cookie(session.cookie()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void zipDownloadContainsFilesAndNestedFolders() throws Exception {
        Session session = register();
        String photos = createFolder(session, "Photos", null);
        String trip = createFolder(session, "Trip", photos);
        upload(session, "a.txt", "A", photos);
        upload(session, "b.txt", "B", trip);
        String loose = upload(session, "loose.txt", "L", null);

        MvcResult pending = mvc.perform(get("/api/v1/files/download-zip")
                        .param("folderIds", photos)
                        .param("fileIds", loose)
                        .cookie(session.cookie()))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult result = mvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, not(containsString("Photos.zip"))))
                .andReturn();

        Map<String, String> entries = unzip(result.getResponse().getContentAsByteArray());
        assertThat(entries).containsEntry("loose.txt", "L")
                .containsEntry("Photos/a.txt", "A")
                .containsEntry("Photos/Trip/b.txt", "B")
                .containsKey("Photos/")
                .containsKey("Photos/Trip/");
    }

    private static Map<String, String> unzip(byte[] bytes) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
