package com.andrija.homesiloserver.backup;

import com.andrija.homesiloserver.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BackupIntegrationTest extends IntegrationTest {

    @Test
    void adminsSeeHowBackupsStand() throws Exception {
        Session admin = admin();
        mvc.perform(get("/api/v1/admin/backups").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.keep").value(7))
                .andExpect(jsonPath("$.schedule").value("0 0 2 * * *"))
                .andExpect(jsonPath("$.backups").isArray());
    }

    @Test
    void backupsAreForAdminsOnly() throws Exception {
        Session user = register();
        mvc.perform(get("/api/v1/admin/backups").cookie(user.cookie())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/backups").cookie(user.cookie()).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/backups")).andExpect(status().isUnauthorized());
    }
}
