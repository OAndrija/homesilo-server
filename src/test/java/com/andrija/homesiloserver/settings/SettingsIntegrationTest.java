package com.andrija.homesiloserver.settings;

import com.andrija.homesiloserver.support.IntegrationTest;
import com.andrija.homesiloserver.settings.dto.SettingsResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every test shares one server, so each puts the settings back as they were. */
class SettingsIntegrationTest extends IntegrationTest {

    @Autowired
    private ServerSettings settings;

    private Session admin;
    private SettingsResponse original;

    @BeforeEach
    void remember() {
        original = settings.get();
    }

    @AfterEach
    void putBack() throws Exception {
        if (admin == null) return;
        change(admin, json("registrationOpen", original.registrationOpen(),
                "defaultQuotaBytes", original.defaultQuotaBytes(),
                "trashRetentionDays", original.trashRetentionDays(),
                "activityRetentionDays", original.activityRetentionDays())).andExpect(status().isOk());
    }

    @Test
    void theStartingValuesComeFromTheEnvironment() throws Exception {
        admin = admin();
        mvc.perform(get("/api/v1/admin/settings").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationOpen").value(true))
                .andExpect(jsonPath("$.trashRetentionDays").value(30))
                .andExpect(jsonPath("$.defaultQuotaBytes").value(original.defaultQuotaBytes()))
                .andExpect(jsonPath("$.maxUploadBytes").isNumber());
    }

    @Test
    void closingSignUpTakesEffectAtOnce() throws Exception {
        admin = admin();
        change(admin, "{\"registrationOpen\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationOpen").value(false));

        String username = "late-" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", username, "email", username + "@example.com", "password", PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/config")).andExpect(jsonPath("$.registrationEnabled").value(false));
    }

    @Test
    void newAccountsGetTheDefaultStorageAndOldOnesKeepTheirs() throws Exception {
        admin = admin();
        Session before = register();
        change(admin, "{\"defaultQuotaBytes\":5368709120}").andExpect(status().isOk());
        Session after = register();

        assertThat(userRepository.findById(after.id()).orElseThrow().getStorageQuotaBytes()).isEqualTo(5368709120L);
        assertThat(userRepository.findById(before.id()).orElseThrow().getStorageQuotaBytes())
                .isEqualTo(original.defaultQuotaBytes());
    }

    @Test
    void onlyTheNamedSettingsChangeAndEachChangeIsLogged() throws Exception {
        admin = admin();
        change(admin, "{\"trashRetentionDays\":7}")
                .andExpect(jsonPath("$.trashRetentionDays").value(7))
                .andExpect(jsonPath("$.registrationOpen").value(original.registrationOpen()));
        assertThat(settings.trashRetentionDays()).isEqualTo(7);

        mvc.perform(get("/api/v1/admin/activity").param("userId", admin.id().toString())
                        .param("category", "ADMIN").cookie(admin.cookie()))
                .andExpect(jsonPath("$.content[?(@.action == 'SETTING_CHANGED' && @.targetName == 'trash.retention-days'"
                        + " && @.oldValue == '" + original.trashRetentionDays() + "' && @.newValue == '7')]").exists());
    }

    @Test
    void valuesAreChecked() throws Exception {
        admin = admin();
        change(admin, "{\"trashRetentionDays\":-1}").andExpect(status().isBadRequest());
        change(admin, "{\"defaultQuotaBytes\":-5}").andExpect(status().isBadRequest());
        change(admin, "{\"activityRetentionDays\":99999}").andExpect(status().isBadRequest());
    }

    @Test
    void settingsAreForAdmins() throws Exception {
        Session user = register();
        mvc.perform(get("/api/v1/admin/settings").cookie(user.cookie())).andExpect(status().isForbidden());
        change(user, "{\"registrationOpen\":true}").andExpect(status().isForbidden());
    }

    private ResultActions change(Session session, String body) throws Exception {
        return mvc.perform(patch("/api/v1/admin/settings").cookie(session.cookie()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
