package com.andrija.homesiloserver.activity;

import com.andrija.homesiloserver.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActivityFlowIntegrationTest extends IntegrationTest {

    @Test
    void fileChangesAreLoggedNewestFirstWithBeforeAndAfter() throws Exception {
        Session admin = admin();
        Session user = register();
        String folder = createFolder(user, "Photos", null);
        String id = upload(user, "draft.txt", "hello", null);
        mvc.perform(patch("/api/v1/files/{id}/rename", id).cookie(user.cookie()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json("name", "final.txt")))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/files/{id}/move", id).cookie(user.cookie()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json("targetFolderId", folder)))
                .andExpect(status().isOk());
        trashFile(user, id);

        String body = activity(admin, "userId", user.id().toString(), "category", "FILES");
        assertThat(JsonPath.<List<String>>read(body, "$.content[*].action"))
                .containsExactly("FILE_TRASHED", "FILE_MOVED", "FILE_RENAMED", "FILE_UPLOADED", "FOLDER_CREATED");
        assertThat(JsonPath.<String>read(body, "$.content[2].oldValue")).isEqualTo("draft.txt");
        assertThat(JsonPath.<String>read(body, "$.content[2].newValue")).isEqualTo("final.txt");
        assertThat(JsonPath.<String>read(body, "$.content[1].newValue")).isEqualTo("Photos");
        assertThat(JsonPath.<String>read(body, "$.content[3].newValue")).isEqualTo("5");
        assertThat(JsonPath.<String>read(body, "$.content[0].actorUsername")).isEqualTo(user.username());
        assertThat(JsonPath.<String>read(body, "$.content[0].ipAddress")).isNotBlank();
    }

    @Test
    void failedOperationsAreNotLogged() throws Exception {
        Session admin = admin();
        Session user = register();
        String id = upload(user, "report.pdf", "data", null);
        mvc.perform(patch("/api/v1/files/{id}/rename", id).cookie(user.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("name", "report.i")))
                .andExpect(status().isBadRequest());

        String body = activity(admin, "userId", user.id().toString(), "category", "FILES");
        assertThat(JsonPath.<List<String>>read(body, "$.content[*].action")).containsExactly("FILE_UPLOADED");
    }

    @Test
    void peopleSeeFailedSignInsAndAdminChangesToTheirOwnAccountButNotFileActivity() throws Exception {
        Session admin = admin();
        Session user = register();
        upload(user, "notes.txt", "x", null);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", user.username(), "password", "not-the-password")))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/admin/users/{id}", user.id()).cookie(admin.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("storageQuotaBytes", 1024)))
                .andExpect(status().isOk());

        MvcResult result = mvc.perform(get("/api/v1/users/me/activity").cookie(user.cookie()))
                .andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonPath.<List<String>>read(body, "$.content[*].action"))
                .containsExactly("USER_STORAGE_LIMIT_CHANGED", "SIGN_IN_FAILED", "SIGNED_UP");
        assertThat(JsonPath.<String>read(body, "$.content[0].actorUsername")).isEqualTo(admin.username());
        assertThat(JsonPath.<String>read(body, "$.content[0].newValue")).isEqualTo("1024");
        assertThat(JsonPath.<String>read(body, "$.content[1].newValue")).isEqualTo("wrong password");
    }

    @Test
    void searchMatchesPeopleAndNames() throws Exception {
        Session admin = admin();
        Session user = register();
        upload(user, "very-unusual-name-" + user.username() + ".txt", "x", null);
        String body = activity(admin, "query", "very-unusual-name-" + user.username());
        assertThat(JsonPath.<List<String>>read(body, "$.content[*].action")).containsExactly("FILE_UPLOADED");
    }

    @Test
    void onlyAdminsCanReadTheServerLog() throws Exception {
        admin();
        Session user = register();
        mvc.perform(get("/api/v1/admin/activity").cookie(user.cookie())).andExpect(status().isForbidden());
    }

    private String activity(Session admin, String... params) throws Exception {
        var request = get("/api/v1/admin/activity").cookie(admin.cookie());
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        return mvc.perform(request).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
