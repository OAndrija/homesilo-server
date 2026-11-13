package com.andrija.homesiloserver.invite;

import com.andrija.homesiloserver.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InviteIntegrationTest extends IntegrationTest {

    @Autowired
    private InviteRepository invites;

    @Test
    void anInviteIsMadeWithItsLinkShownOnce() throws Exception {
        Session admin = admin();
        MvcResult result = create(admin, "{\"expiresInDays\":3,\"note\":\"  For Ben  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.invite.status").value("PENDING"))
                .andExpect(jsonPath("$.invite.role").value("USER"))
                .andExpect(jsonPath("$.invite.note").value("For Ben"))
                .andExpect(jsonPath("$.invite.createdBy").value(admin.username()))
                .andReturn();
        String token = read(result, "$.token");
        String id = read(result, "$.invite.id");
        assertThat(token).matches("[A-Za-z0-9_-]{43}");

        // Only a hash is kept; the list never shows the link again.
        Invite stored = invites.findById(UUID.fromString(id)).orElseThrow();
        assertThat(stored.getTokenHash()).isEqualTo(InviteService.hash(token)).doesNotContain(token);
        assertThat(stored.getExpiresAt()).isBetween(LocalDateTime.now().plusDays(3).minusMinutes(1),
                LocalDateTime.now().plusDays(3).plusMinutes(1));
        String list = mvc.perform(get("/api/v1/admin/invites").cookie(admin.cookie()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).contains(id).doesNotContain(token).doesNotContain("tokenHash");
    }

    @Test
    void anInviteCanMakeAnAdmin() throws Exception {
        Session admin = admin();
        String token = read(create(admin, "{\"role\":\"ADMIN\"}").andReturn(), "$.token");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", "invited-admin-" + UUID.randomUUID().toString().substring(0, 6),
                                "email", UUID.randomUUID() + "@example.com", "password", PASSWORD,
                                "inviteToken", token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void aFailedSignUpLeavesTheInviteUnused() throws Exception {
        Session admin = admin();
        Session existing = register();
        String token = read(create(admin, "{}").andReturn(), "$.token");

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", existing.username(), "email", "new-" + existing.username() + "@example.com",
                                "password", PASSWORD, "inviteToken", token)))
                .andExpect(status().isConflict());
        preview(token).andExpect(status().isOk());
    }

    @Test
    void expiredRevokedAndMadeUpLinksDontWork() throws Exception {
        Session admin = admin();
        MvcResult expiring = create(admin, "{}").andReturn();
        Invite invite = invites.findById(UUID.fromString(read(expiring, "$.invite.id"))).orElseThrow();
        invite.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        invites.save(invite);
        preview(read(expiring, "$.token")).andExpect(status().isNotFound());

        MvcResult revoked = create(admin, "{}").andReturn();
        mvc.perform(delete("/api/v1/admin/invites/{id}", read(revoked, "$.invite.id")).cookie(admin.cookie()).with(csrf()))
                .andExpect(status().isNoContent());
        preview(read(revoked, "$.token")).andExpect(status().isNotFound());

        preview("x".repeat(43)).andExpect(status().isNotFound());
        preview("../../etc/passwd").andExpect(status().isNotFound());
    }

    @Test
    void aUsedInviteCantBeRevoked() throws Exception {
        Session admin = admin();
        MvcResult created = create(admin, "{}").andReturn();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json("username", "joiner-" + UUID.randomUUID().toString().substring(0, 6),
                        "email", UUID.randomUUID() + "@example.com", "password", PASSWORD,
                        "inviteToken", read(created, "$.token"))))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/admin/invites/{id}", read(created, "$.invite.id")).cookie(admin.cookie()).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invitesAreForAdminsAndChecked() throws Exception {
        Session user = register();
        mvc.perform(post("/api/v1/admin/invites").cookie(user.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/invites").cookie(user.cookie())).andExpect(status().isForbidden());

        Session admin = admin();
        create(admin, "{\"expiresInDays\":0}").andExpect(status().isBadRequest());
        create(admin, "{\"expiresInDays\":31}").andExpect(status().isBadRequest());
        create(admin, "{\"role\":\"OWNER\"}").andExpect(status().isBadRequest());
        create(admin, "{\"note\":\"" + "x".repeat(81) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void makingAnInviteIsInTheActivityLog() throws Exception {
        Session admin = admin();
        create(admin, "{\"note\":\"For the log\"}").andExpect(status().isCreated());
        mvc.perform(get("/api/v1/admin/activity").param("userId", admin.id().toString())
                        .param("category", "ADMIN").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action == 'INVITE_CREATED' && @.targetName == 'For the log')]")
                        .exists());
    }

    private ResultActions create(Session admin, String body) throws Exception {
        return mvc.perform(post("/api/v1/admin/invites").cookie(admin.cookie()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions preview(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/invites/preview").contentType(MediaType.APPLICATION_JSON)
                .content(json("token", token)));
    }
}
