package com.andrija.homesiloserver.user;

import com.andrija.homesiloserver.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserPreferencesIntegrationTest extends IntegrationTest {

    private static final String PREFERENCES = "/api/v1/users/me/preferences";

    @Test
    void aNewAccountHasOnlyDefaults() throws Exception {
        Session user = register();
        mvc.perform(get(PREFERENCES).cookie(user.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values").isEmpty())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void onlyTheNamedSettingsChange() throws Exception {
        Session user = register();
        change(user, """
                {"values":{"theme.mode":"dark","theme.dark":"gruvbox-material-hard","appearance.spacing":"compact"}}
                """).andExpect(status().isOk());
        change(user, """
                {"values":{"view.my-files":"grid","sort.my-files":"name,asc","appearance.spacing":"spacious"}}
                """).andExpect(status().isOk());

        assertThat(values(user)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "theme.mode", "dark",
                "theme.dark", "gruvbox-material-hard",
                "appearance.spacing", "spacious",
                "view.my-files", "grid",
                "sort.my-files", "name,asc"));
    }

    @Test
    void nullPutsASettingBackToItsDefault() throws Exception {
        Session user = register();
        change(user, "{\"values\":{\"appearance.corners\":\"sharp\",\"appearance.font\":\"readable\"}}")
                .andExpect(status().isOk());
        change(user, "{\"values\":{\"appearance.corners\":null}}").andExpect(status().isOk());
        assertThat(values(user)).containsExactly(Map.entry("appearance.font", "readable"));

        // With nothing left changed, the account is back to having no settings at all.
        change(user, "{\"values\":{\"appearance.font\":null}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values").isEmpty());
        mvc.perform(get(PREFERENCES).cookie(user.cookie())).andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void eachPersonHasTheirOwnSettings() throws Exception {
        Session ana = register();
        Session ben = register();
        change(ana, "{\"values\":{\"appearance.spacing\":\"compact\",\"theme.mode\":\"light\"}}")
                .andExpect(status().isOk());
        change(ben, "{\"values\":{\"appearance.spacing\":\"spacious\"}}").andExpect(status().isOk());

        assertThat(values(ana)).containsEntry("appearance.spacing", "compact").containsEntry("theme.mode", "light");
        assertThat(values(ben)).containsExactly(Map.entry("appearance.spacing", "spacious"));
    }

    @Test
    void unknownSettingsAndValuesAreRefused() throws Exception {
        Session user = register();
        change(user, "{\"values\":{\"appearance.spacing\":\"huge\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Spacing must be compact, comfortable or spacious"));
        change(user, "{\"values\":{\"theme.mode\":\"sepia\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Theme mode must be light, dark or system"));
        change(user, "{\"values\":{\"theme.dark\":\"<script>\"}}").andExpect(status().isBadRequest());
        change(user, "{\"values\":{\"view.my-files\":\"tiles\"}}").andExpect(status().isBadRequest());
        change(user, "{\"values\":{\"sort.my-files\":\"name\"}}").andExpect(status().isBadRequest());
        change(user, "{\"values\":{\"isAdmin\":\"true\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("There is no setting called \"isAdmin\""));
        change(user, "{\"values\":{}}").andExpect(status().isBadRequest());
        change(user, "{}").andExpect(status().isBadRequest());

        // A bad setting in a batch keeps the good ones in it from being saved too.
        change(user, "{\"values\":{\"appearance.font\":\"readable\",\"appearance.cards\":\"shiny\"}}")
                .andExpect(status().isBadRequest());
        assertThat(values(user)).isEmpty();
    }

    @Test
    void anAccountKeepsAtMost64Settings() throws Exception {
        Session user = register();
        for (int batch = 0; batch < 2; batch++) {
            StringBuilder body = new StringBuilder("{\"values\":{");
            for (int i = 0; i < 32; i++) {
                if (i > 0) body.append(',');
                body.append("\"view.page-").append(batch).append('-').append(i).append("\":\"grid\"");
            }
            change(user, body.append("}}").toString()).andExpect(status().isOk());
        }
        change(user, "{\"values\":{\"view.one-more\":\"grid\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("An account can keep at most 64 settings"));
        // Changing one that's already kept is still fine.
        change(user, "{\"values\":{\"view.page-0-0\":\"list\"}}").andExpect(status().isOk());
    }

    @Test
    void theSettingsGoAwayWithTheAccount() throws Exception {
        Session admin = admin();
        Session user = register();
        change(user, "{\"values\":{\"appearance.spacing\":\"compact\"}}").andExpect(status().isOk());
        mvc.perform(delete("/api/v1/admin/users/{id}", user.id()).cookie(admin.cookie()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void signedOutVisitorsCantReadOrChange() throws Exception {
        mvc.perform(get(PREFERENCES)).andExpect(status().isUnauthorized());
        mvc.perform(patch(PREFERENCES).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"theme.mode\":\"dark\"}}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions change(Session session, String body) throws Exception {
        return mvc.perform(patch(PREFERENCES).cookie(session.cookie()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Map<String, String> values(Session session) throws Exception {
        String body = mvc.perform(get(PREFERENCES).cookie(session.cookie())).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.values");
    }
}
