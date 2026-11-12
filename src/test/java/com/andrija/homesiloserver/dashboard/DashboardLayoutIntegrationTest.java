package com.andrija.homesiloserver.dashboard;

import com.andrija.homesiloserver.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardLayoutIntegrationTest extends IntegrationTest {

    private static final String LAYOUT = "/api/v1/dashboard/layout";

    @Test
    void aNewAccountHasTheDefaultLayout() throws Exception {
        Session user = register();
        mvc.perform(get(LAYOUT).cookie(user.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customized").value(false))
                .andExpect(jsonPath("$.tiles").isEmpty());
    }

    @Test
    void savedTilesComeBackInOrderAndCanBeChangedAndReset() throws Exception {
        Session user = register();
        save(user, """
                {"tiles":[{"id":"recent","size":"wide"},{"id":"storage","size":"large"},{"id":"total-files","size":"small"}]}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.customized").value(true));

        String body = layout(user);
        assertThat(JsonPath.<List<String>>read(body, "$.tiles[*].id")).containsExactly("recent", "storage", "total-files");
        assertThat(JsonPath.<List<String>>read(body, "$.tiles[*].size")).containsExactly("wide", "large", "small");

        // Fewer tiles and a new order replace the old list entirely.
        save(user, """
                {"tiles":[{"id":"total-files","size":"medium"},{"id":"recent","size":"wide"}]}
                """).andExpect(status().isOk());
        assertThat(JsonPath.<List<String>>read(layout(user), "$.tiles[*].id")).containsExactly("total-files", "recent");

        // An empty dashboard is a choice too, not the default.
        save(user, "{\"tiles\":[]}").andExpect(status().isOk());
        assertThat(JsonPath.<Boolean>read(layout(user), "$.customized")).isTrue();

        mvc.perform(delete(LAYOUT).cookie(user.cookie()).with(csrf())).andExpect(status().isNoContent());
        assertThat(JsonPath.<Boolean>read(layout(user), "$.customized")).isFalse();
    }

    @Test
    void eachPersonHasTheirOwnLayout() throws Exception {
        Session ana = register();
        Session ben = register();
        save(ana, "{\"tiles\":[{\"id\":\"starred\",\"size\":\"wide\"}]}").andExpect(status().isOk());
        assertThat(JsonPath.<Boolean>read(layout(ben), "$.customized")).isFalse();
    }

    @Test
    void badLayoutsAreRefused() throws Exception {
        Session user = register();
        save(user, "{\"tiles\":[{\"id\":\"storage\",\"size\":\"huge\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Size must be small, medium, large or wide"));
        save(user, "{\"tiles\":[{\"id\":\"Storage!\",\"size\":\"small\"}]}")
                .andExpect(status().isBadRequest());
        save(user, "{\"tiles\":[{\"id\":\"storage\",\"size\":\"small\"},{\"id\":\"storage\",\"size\":\"wide\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Each tile can only be on the dashboard once"));
        save(user, "{}").andExpect(status().isBadRequest());

        StringBuilder many = new StringBuilder("{\"tiles\":[");
        for (int i = 0; i < 33; i++) {
            if (i > 0) many.append(',');
            many.append("{\"id\":\"tile-").append(i).append("\",\"size\":\"small\"}");
        }
        save(user, many.append("]}").toString()).andExpect(status().isBadRequest());

        assertThat(JsonPath.<Boolean>read(layout(user), "$.customized")).isFalse();
    }

    @Test
    void theLayoutGoesAwayWithTheAccount() throws Exception {
        Session admin = admin();
        Session user = register();
        save(user, "{\"tiles\":[{\"id\":\"storage\",\"size\":\"large\"}]}").andExpect(status().isOk());
        mvc.perform(delete("/api/v1/admin/users/{id}", user.id()).cookie(admin.cookie()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void signedOutVisitorsCantReadOrSave() throws Exception {
        mvc.perform(get(LAYOUT)).andExpect(status().isUnauthorized());
        mvc.perform(put(LAYOUT).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"tiles\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions save(Session session, String body) throws Exception {
        return mvc.perform(put(LAYOUT).cookie(session.cookie()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String layout(Session session) throws Exception {
        return mvc.perform(get(LAYOUT).cookie(session.cookie())).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
