package com.andrija.homesiloserver.bdd;

import com.jayway.jsonpath.JsonPath;
import io.cucumber.spring.ScenarioScope;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * What one scenario knows: the people in it, who "I" am, the files and folders I made
 * (by name) and the last response. A fresh instance per scenario keeps them independent.
 *
 * <p>All scenarios share one database, so the names in a scenario ("ana") are only
 * display names; each gets a unique username behind the scenes.
 */
@ScenarioScope
public class World {

    static final String PASSWORD = "correct-horse-battery";

    private final MockMvc mvc;
    private final Map<String, Account> accounts = new HashMap<>();
    private final Map<String, String> fileIds = new HashMap<>();
    private final Map<String, String> folderIds = new HashMap<>();
    private Account me;
    private MvcResult last;

    public World(MockMvc mvc) {
        this.mvc = mvc;
    }

    public record Account(String name, String username, UUID id, Cookie cookie) {}

    // ── People ───────────────────────────────────────────────────────────────

    /** Signs up a new person; returns the account, or null when the server refused. */
    Account signUp(String name) {
        String username = name + "-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("username", username, "email", username + "@example.com", "password", PASSWORD)));
        if (result.getResponse().getStatus() != 201) return null;
        Account account = new Account(name, username, UUID.fromString(read(result, "$.user.id")),
                result.getResponse().getCookie("homesilo_session"));
        accounts.put(name, account);
        return account;
    }

    /** The person with this name, signing them up first if they aren't in the scenario yet. */
    Account account(String name) {
        Account existing = accounts.get(name);
        if (existing != null) return existing;
        Account created = signUp(name);
        assertThat(created).as("sign-up of %s", name).isNotNull();
        return created;
    }

    void signInAs(String name) {
        me = account(name);
    }

    Account me() {
        assertThat(me).as("someone is signed in").isNotNull();
        return me;
    }

    // ── My files and folders ─────────────────────────────────────────────────

    MvcResult upload(String fileName, String content, String folderName) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/files/upload");
        request.file(new MockMultipartFile("file", fileName, "application/octet-stream",
                content.getBytes(StandardCharsets.UTF_8)));
        if (folderName != null) request.param("folderId", folderId(folderName));
        MvcResult result = perform(asMe(request));
        if (result.getResponse().getStatus() == 201) fileIds.put(fileName, read(result, "$.id"));
        return result;
    }

    void rememberFolder(String name, String id) {
        folderIds.put(name, id);
    }

    void renamedFile(String from, String to) {
        fileIds.put(to, fileIds.remove(from));
    }

    boolean isFolder(String name) {
        return folderIds.containsKey(name);
    }

    String fileId(String name) {
        assertThat(fileIds).as("files I uploaded").containsKey(name);
        return fileIds.get(name);
    }

    String folderId(String name) {
        assertThat(folderIds).as("folders I made").containsKey(name);
        return folderIds.get(name);
    }

    /** Names of the folders and files directly in a listing from {@code /folders/root} or {@code /contents}. */
    static List<String> namesIn(MvcResult contents) {
        List<String> names = new ArrayList<>(JsonPath.<List<String>>read(body(contents), "$.subfolders[*].name"));
        names.addAll(JsonPath.<List<String>>read(body(contents), "$.files.content[*].originalFileName"));
        return names;
    }

    // ── Requests ─────────────────────────────────────────────────────────────

    /** Sends the request as me, with the CSRF token the browser app would send. */
    <B extends AbstractMockHttpServletRequestBuilder<B>> B asMe(B request) {
        return request.cookie(me().cookie()).with(csrf());
    }

    MvcResult perform(RequestBuilder request) {
        try {
            last = mvc.perform(request).andReturn();
            return last;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    MvcResult last() {
        assertThat(last).as("a request was made").isNotNull();
        return last;
    }

    static String body(MvcResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static String read(MvcResult result, String path) {
        Object value = JsonPath.read(body(result), path);
        return value == null ? null : value.toString();
    }

    /** Minimal JSON object builder: {@code json("a", "x", "b", null)}. */
    static String json(Object... keyValues) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) out.append(',');
            Object value = keyValues[i + 1];
            out.append('"').append(keyValues[i]).append("\":");
            out.append(value == null ? "null"
                    : '"' + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + '"');
        }
        return out.append('}').toString();
    }
}
