package com.andrija.homesiloserver.upload;

import com.andrija.homesiloserver.support.IntegrationTest;
import com.andrija.homesiloserver.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Chunks are 1 KB in tests (see the test application.properties). */
class ChunkedUploadIntegrationTest extends IntegrationTest {

    private static final int CHUNK = 1024;

    @Autowired
    private UploadSessionRepository sessions;
    @Autowired
    private UploadService uploads;

    @Test
    void aFileSentInChunksInAnyOrderWithRetriesArrivesWhole() throws Exception {
        Session user = register();
        byte[] content = bytes(2 * CHUNK + 300);
        MvcResult started = start(user, "holiday.mov", content.length, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chunkSize").value(CHUNK))
                .andExpect(jsonPath("$.chunkCount").value(3))
                .andExpect(jsonPath("$.receivedChunks").isEmpty())
                .andReturn();
        String id = read(started, "$.id");

        chunk(user, id, 2, content).andExpect(status().isNoContent());
        chunk(user, id, 0, content).andExpect(status().isNoContent());
        chunk(user, id, 0, content).andExpect(status().isNoContent()); // a retry
        chunk(user, id, 1, content).andExpect(status().isNoContent());

        String fileId = read(mvc.perform(post("/api/v1/uploads/{id}/complete", id).cookie(user.cookie()).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFileName").value("holiday.mov"))
                .andExpect(jsonPath("$.size").value(content.length))
                .andReturn(), "$.id");

        byte[] downloaded = mvc.perform(get("/api/v1/files/{id}/download", fileId).cookie(user.cookie()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(content);
        // Done: the session and its pieces are gone.
        mvc.perform(get("/api/v1/uploads/{id}", id).cookie(user.cookie())).andExpect(status().isNotFound());
    }

    @Test
    void anInterruptedUploadSaysWhatsMissing() throws Exception {
        Session user = register();
        byte[] content = bytes(3 * CHUNK);
        String id = read(start(user, "big.iso", content.length, null).andReturn(), "$.id");
        chunk(user, id, 0, content);
        chunk(user, id, 2, content);

        mvc.perform(get("/api/v1/uploads/{id}", id).cookie(user.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedChunks[0]").value(0))
                .andExpect(jsonPath("$.receivedChunks[1]").value(2));
        mvc.perform(post("/api/v1/uploads/{id}/complete", id).cookie(user.cookie()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("1 chunk hasn't arrived yet"));

        chunk(user, id, 1, content);
        mvc.perform(post("/api/v1/uploads/{id}/complete", id).cookie(user.cookie()).with(csrf()))
                .andExpect(status().isCreated());
    }

    @Test
    void chunksMustBeTheRightSizeAndPlace() throws Exception {
        Session user = register();
        byte[] content = bytes(CHUNK + 10);
        String id = read(start(user, "a.bin", content.length, null).andReturn(), "$.id");

        mvc.perform(put("/api/v1/uploads/{id}/chunks/{i}", id, 0).cookie(user.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(new byte[CHUNK - 1]))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/uploads/{id}/chunks/{i}", id, 1).cookie(user.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(new byte[CHUNK]))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/uploads/{id}/chunks/{i}", id, 2).cookie(user.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(new byte[10]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("This upload has chunks 0 to 1"));
        mvc.perform(get("/api/v1/uploads/{id}", id).cookie(user.cookie()))
                .andExpect(jsonPath("$.receivedChunks").isEmpty());
    }

    @Test
    void unfinishedUploadsCountAgainstTheQuota() throws Exception {
        Session user = register();
        User account = userRepository.findById(user.id()).orElseThrow();
        account.setStorageQuotaBytes(5L * CHUNK);
        userRepository.save(account);

        start(user, "first.bin", 3L * CHUNK, null).andExpect(status().isCreated());
        start(user, "second.bin", 3L * CHUNK, null)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.detail").value("Not enough storage space left for 'second.bin'"));
    }

    @Test
    void onlyTheUploaderCanSeeOrTouchAnUpload() throws Exception {
        Session owner = register();
        Session other = register();
        byte[] content = bytes(CHUNK);
        String id = read(start(owner, "mine.bin", content.length, null).andReturn(), "$.id");

        mvc.perform(get("/api/v1/uploads/{id}", id).cookie(other.cookie())).andExpect(status().isNotFound());
        chunk(other, id, 0, content).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/uploads/{id}/complete", id).cookie(other.cookie()).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/uploads/{id}", id).cookie(other.cookie()).with(csrf()))
                .andExpect(status().isNotFound());
        // Nor upload into someone else's folder.
        String folderId = createFolder(owner, "Private", null);
        start(other, "sneaky.bin", 10, folderId).andExpect(status().isNotFound());
    }

    @Test
    void aCancelledUploadIsGone() throws Exception {
        Session user = register();
        String id = read(start(user, "oops.bin", CHUNK, null).andReturn(), "$.id");
        chunk(user, id, 0, bytes(CHUNK));
        mvc.perform(delete("/api/v1/uploads/{id}", id).cookie(user.cookie()).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/uploads/{id}", id).cookie(user.cookie())).andExpect(status().isNotFound());
    }

    @Test
    void uploadsLeftIdleForADayAreClearedOut() throws Exception {
        Session user = register();
        String id = read(start(user, "forgotten.bin", CHUNK, null).andReturn(), "$.id");
        UploadSession session = sessions.findById(UUID.fromString(id)).orElseThrow();
        session.setUpdatedAt(LocalDateTime.now().minusDays(2));
        sessions.save(session);

        uploads.clearAbandoned();

        assertThat(sessions.existsById(UUID.fromString(id))).isFalse();
    }

    @Test
    void namesAreCleanedUpAndSizesChecked() throws Exception {
        Session user = register();
        start(user, "../../etc/passwd", 10, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("passwd"));
        start(user, "empty.txt", 0, null).andExpect(status().isBadRequest());
        start(user, "huge.bin", 6L * 1024 * 1024 * 1024, null).andExpect(status().isPayloadTooLarge());
    }

    private ResultActions start(Session user, String name, long size, String folderId) throws Exception {
        String body = folderId == null ? json("fileName", name, "size", size)
                : json("fileName", name, "size", size, "folderId", folderId);
        return mvc.perform(post("/api/v1/uploads").cookie(user.cookie()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions chunk(Session user, String id, int index, byte[] content) throws Exception {
        int from = index * CHUNK;
        byte[] piece = Arrays.copyOfRange(content, from, Math.min(content.length, from + CHUNK));
        return mvc.perform(put("/api/v1/uploads/{id}/chunks/{i}", id, index).cookie(user.cookie()).with(csrf())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(piece));
    }

    private static byte[] bytes(int length) {
        byte[] b = new byte[length];
        new Random(length).nextBytes(b);
        return b;
    }
}
