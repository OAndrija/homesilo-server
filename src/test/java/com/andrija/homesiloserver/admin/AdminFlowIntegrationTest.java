package com.andrija.homesiloserver.admin;

import com.andrija.homesiloserver.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminFlowIntegrationTest extends IntegrationTest {

    @Test
    void regularUsersCantReachAnyAdminEndpoint() throws Exception {
        admin(); // so the next account isn't the server's first (which becomes admin)
        Session user = register();
        mvc.perform(get("/api/v1/admin/overview").cookie(user.cookie())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").cookie(user.cookie())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/users/{id}", user.id()).cookie(user.cookie()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listShowsUsageAndLastSignInAndCanBeFiltered() throws Exception {
        Session admin = admin();
        Session user = register();
        upload(user, "a.txt", "12345", null);
        String trashed = upload(user, "b.txt", "678", null);
        trashFile(user, trashed);

        mvc.perform(get("/api/v1/admin/users").param("query", user.username()).cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value(user.username()))
                .andExpect(jsonPath("$.content[0].storageUsedBytes").value(8)) // trash counts
                .andExpect(jsonPath("$.content[0].fileCount").value(1)) // trash doesn't
                .andExpect(jsonPath("$.content[0].lastLoginAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].password").doesNotExist());

        mvc.perform(get("/api/v1/admin/users").param("query", user.username()).param("role", "ADMIN")
                        .cookie(admin.cookie()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/admin/users").param("query", user.username()).param("status", "DISABLED")
                        .cookie(admin.cookie()))
                .andExpect(jsonPath("$.totalElements").value(0));
        // LIKE wildcards in the search are matched literally.
        mvc.perform(get("/api/v1/admin/users").param("query", "%").cookie(admin.cookie()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void sortingIsLimitedToRealColumns() throws Exception {
        Session admin = admin();
        mvc.perform(get("/api/v1/admin/users").param("sort", "lastLoginAt,desc").cookie(admin.cookie()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/users").param("sort", "password,asc").cookie(admin.cookie()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overviewCountsAccountsAndStorage() throws Exception {
        Session admin = admin();
        Session user = register();
        upload(user, "c.txt", "123", null);
        mvc.perform(get("/api/v1/admin/overview").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isNumber())
                .andExpect(jsonPath("$.admins").isNumber())
                .andExpect(jsonPath("$.defaultQuotaBytes").isNumber())
                .andExpect(result -> assertThat(read(result, "$.storageUsedBytes")).isNotEqualTo("0"));
    }

    @Test
    void addedUsersCanSignInAndNamesStayUnique() throws Exception {
        Session admin = admin();
        String username = "added-" + System.nanoTime();
        mvc.perform(post("/api/v1/admin/users").cookie(admin.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", username, "email", username + "@example.com",
                                "password", "a-temporary-password", "role", "USER", "storageQuotaBytes", 1024)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storageQuotaBytes").value(1024))
                .andExpect(jsonPath("$.lastLoginAt").value(nullValue()));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", username, "password", "a-temporary-password")))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/admin/users").cookie(admin.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", username.toUpperCase(), "email", "other-" + username + "@example.com",
                                "password", "a-temporary-password", "role", "USER")))
                .andExpect(status().isConflict());
    }

    @Test
    void disablingAnAccountEndsItsSessions() throws Exception {
        Session admin = admin();
        Session user = register();
        mvc.perform(patch("/api/v1/admin/users/{id}", user.id()).cookie(admin.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("enabled", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(get("/api/v1/users/me").cookie(user.cookie())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", user.username(), "password", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("This account has been disabled"));
    }

    @Test
    void resettingAPasswordSignsThePersonOutEverywhere() throws Exception {
        Session admin = admin();
        Session user = register();
        Thread.sleep(1100); // JWT iat has one-second resolution
        mvc.perform(put("/api/v1/admin/users/{id}/password", user.id()).cookie(admin.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("newPassword", "brand-new-password")))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/users/me").cookie(user.cookie())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", user.username(), "password", "brand-new-password")))
                .andExpect(status().isOk());
    }

    @Test
    void adminsCantLockThemselvesOut() throws Exception {
        Session admin = admin();
        mvc.perform(patch("/api/v1/admin/users/{id}", admin.id()).cookie(admin.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("enabled", false)))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/admin/users/{id}", admin.id()).cookie(admin.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("role", "USER")))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/admin/users/{id}/password", admin.id()).cookie(admin.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("newPassword", "brand-new-password")))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/admin/users/{id}", admin.id()).cookie(admin.cookie()).with(csrf()))
                .andExpect(status().isConflict());
        // Changing their own storage limit is fine.
        mvc.perform(patch("/api/v1/admin/users/{id}", admin.id()).cookie(admin.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("storageQuotaBytes", 2048)))
                .andExpect(status().isOk());
    }

    @Test
    void deletingAnAccountRemovesItsFilesAndFolders() throws Exception {
        Session admin = admin();
        Session user = register();
        String folder = createFolder(user, "Photos", null);
        createFolder(user, "Trip", folder);
        upload(user, "inside.txt", "inside", folder);
        upload(user, "loose.txt", "loose", null);

        mvc.perform(delete("/api/v1/admin/users/{id}", user.id()).cookie(admin.cookie()).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/admin/users/{id}", user.id()).cookie(admin.cookie()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/users/me").cookie(user.cookie())).andExpect(status().isUnauthorized());
        MvcResult list = mvc.perform(get("/api/v1/admin/users").param("query", user.username()).cookie(admin.cookie()))
                .andReturn();
        assertThat(read(list, "$.totalElements")).isEqualTo("0");
    }
}
