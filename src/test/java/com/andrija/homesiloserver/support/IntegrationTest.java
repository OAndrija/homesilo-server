package com.andrija.homesiloserver.support;

import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack tests against the real security chain, Flyway schema, blob store and a
 * PostgreSQL container. All subclasses share one Spring context and so one database.
 * Every test registers its own users, so tests are independent without needing
 * transactional rollback (which would hide after-commit behaviour).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    public static final String SESSION_COOKIE = "homesilo_session";
    protected static final String PASSWORD = "correct-horse-battery";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected UserRepository userRepository;

    protected Session register() {
        return register("user-" + UUID.randomUUID().toString().substring(0, 8));
    }

    protected Session register(String username) {
        FirstAdmin.ensureSetUp(mvc);
        try {
            MvcResult result = mvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("username", username, "email", username + "@example.com", "password", PASSWORD)))
                    .andExpect(status().isCreated())
                    .andReturn();
            return new Session(UUID.fromString(read(result, "$.user.id")), username,
                    result.getResponse().getCookie(SESSION_COOKIE));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** A new account with the admin role (the role is read from the database on every request). */
    protected Session admin() {
        Session session = register("admin-" + UUID.randomUUID().toString().substring(0, 8));
        User user = userRepository.findById(session.id()).orElseThrow();
        user.setRole(com.andrija.homesiloserver.user.UserRole.ADMIN);
        userRepository.save(user);
        return session;
    }

    protected void setQuota(Session session, long bytes) {
        User user = userRepository.findById(session.id()).orElseThrow();
        user.setStorageQuotaBytes(bytes);
        userRepository.save(user);
    }

    protected String upload(Session session, String fileName, String content, String folderId) throws Exception {
        MvcResult result = mvc.perform(uploadRequest(session, fileName, content, folderId))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result, "$.id");
    }

    protected MockMultipartHttpServletRequestBuilder uploadRequest(
            Session session, String fileName, String content, String folderId) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/files/upload");
        request.file(new MockMultipartFile("file", fileName, "application/octet-stream",
                content.getBytes(StandardCharsets.UTF_8)));
        if (folderId != null) request.param("folderId", folderId);
        request.cookie(session.cookie()).with(csrf());
        return request;
    }

    protected String createFolder(Session session, String name, String parentId) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/folders")
                        .cookie(session.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("name", name, "parentId", parentId)))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result, "$.id");
    }

    protected void trashFile(Session session, String fileId) throws Exception {
        mvc.perform(patch("/api/v1/files/{id}/trash", fileId).cookie(session.cookie()).with(csrf()))
                .andExpect(status().isOk());
    }

    protected void trashFolder(Session session, String folderId) throws Exception {
        mvc.perform(patch("/api/v1/folders/{id}/trash", folderId).cookie(session.cookie()).with(csrf()))
                .andExpect(status().isOk());
    }

    protected static String read(MvcResult result, String path) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
        return value == null ? null : value.toString();
    }

    /** Minimal JSON object builder: {@code json("a", 1, "b", "x", "c", null)}. */
    protected static String json(Object... keyValues) {
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        for (int i = 0; i < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            String rendered = value == null ? "null"
                    : (value instanceof Number || value instanceof Boolean) ? value.toString()
                    : '"' + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + '"';
            joiner.add('"' + keyValues[i].toString() + "\":" + rendered);
        }
        return joiner.toString();
    }

    public record Session(UUID id, String username, Cookie cookie) {}
}
