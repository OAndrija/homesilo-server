package com.andrija.homesiloserver.auth;

import com.andrija.homesiloserver.support.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A fresh install with the default settings (sign-up closed), on its own empty
 * database: the admin is created with the setup code, and everyone else joins
 * through an invite. The steps build on each other, in order.
 */
@SpringBootTest(properties = {
        "homesilo.auth.registration-enabled=false",
        "homesilo.auth.setup-code=FRESH-INSTALL-2026"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FirstRunIntegrationTest {

    private static final String CODE = "FRESH-INSTALL-2026";
    private static Cookie admin;
    private static String token;

    @Autowired
    private MockMvc mvc;

    @Test
    @Order(1)
    void aFreshServerAsksForSetup() throws Exception {
        mvc.perform(get("/api/v1/auth/config"))
                .andExpect(jsonPath("$.setupRequired").value(true))
                .andExpect(jsonPath("$.registrationEnabled").value(false));
        // Nobody can take the server by signing up first.
        register("eager-visitor", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("HomeSilo isn't set up yet. Create the admin account first."));
    }

    @Test
    @Order(2)
    void theWrongCodeIsRefusedAndRepeatedGuessesAreBlocked() throws Exception {
        for (int i = 0; i < 3; i++) {
            setUp("WRONG-CODE-0000", "guesser", "10.0.0.66")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(
                            "That setup code isn't right. Check the server log for the current one."));
        }
        // Blocked even with the right code, from that address, for a while.
        setUp(CODE, "guesser", "10.0.0.66").andExpect(status().isTooManyRequests());
    }

    @Test
    @Order(3)
    void theRightCodeMakesTheAdminAndSignsThemIn() throws Exception {
        MvcResult result = setUp("fresh install 2026", "owner", "10.0.0.1") // case, spaces and dashes don't matter
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value("owner"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn();
        admin = result.getResponse().getCookie("homesilo_session");
        assertThat(admin).isNotNull();
        mvc.perform(get("/api/v1/admin/users").cookie(admin)).andExpect(status().isOk());
    }

    @Test
    @Order(4)
    void setupIsClosedOnceDone() throws Exception {
        mvc.perform(get("/api/v1/auth/config")).andExpect(jsonPath("$.setupRequired").value(false));
        setUp(CODE, "second-owner", "10.0.0.2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("HomeSilo is already set up. Sign in instead."));
        register("stranger", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(
                        "Sign-up is invite-only on this server. Ask an admin for an invite link."));
    }

    @Test
    @Order(5)
    void anInvitedPersonCanJoin() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/admin/invites").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"For Mum\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        token = JsonPath.read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");

        mvc.perform(post("/api/v1/auth/invites/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitedBy").value("owner"));

        register("mum", token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("USER"));
        mvc.perform(get("/api/v1/admin/invites").cookie(admin))
                .andExpect(jsonPath("$[0].status").value("USED"))
                .andExpect(jsonPath("$[0].usedBy").value("mum"))
                .andExpect(jsonPath("$[0].note").value("For Mum"));
    }

    @Test
    @Order(6)
    void anInviteWorksOnce() throws Exception {
        register("mums-friend", token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "This invite link has expired or has already been used. Ask whoever sent it for a new one."));
    }

    private ResultActions setUp(String code, String username, String address) throws Exception {
        return mvc.perform(fromAddress(post("/api/v1/auth/setup"), address)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"setupCode":"%s","username":"%s","email":"%s@example.com","password":"owner-password-1"}
                        """.formatted(code, username, username)));
    }

    private ResultActions register(String username, String inviteToken) throws Exception {
        String invite = inviteToken == null ? "" : ",\"inviteToken\":\"" + inviteToken + "\"";
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"email\":\"%s@example.com\",\"password\":\"password-123\"%s}"
                        .formatted(username, username, invite)));
    }

    private static MockHttpServletRequestBuilder fromAddress(MockHttpServletRequestBuilder request, String address) {
        return request.with(r -> {
            r.setRemoteAddr(address);
            return r;
        });
    }
}
