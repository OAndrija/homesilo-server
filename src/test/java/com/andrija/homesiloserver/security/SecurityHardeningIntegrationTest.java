package com.andrija.homesiloserver.security;

import com.andrija.homesiloserver.support.IntegrationTest;
import com.andrija.homesiloserver.user.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityHardeningIntegrationTest extends IntegrationTest {

    // ── Passwords ────────────────────────────────────────────────────────────

    @Test
    void newPasswordsAreHashedWithArgon2id() {
        Session user = register();
        String hash = userRepository.findById(user.id()).orElseThrow().getPassword();
        assertThat(hash).startsWith("{argon2}$argon2id$v=19$m=19456,t=2,p=1$");
    }

    @Test
    void longPassphrasesWork() throws Exception {
        String passphrase = "this is a long passphrase that goes well past bcrypt's seventy-two byte limit, ok";
        String username = "long-" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", username, "email", username + "@example.com", "password", passphrase)))
                .andExpect(status().isCreated());
        login(username, passphrase).andExpect(status().isOk());
        // Only the first 72 bytes matter to bcrypt; to Argon2 every one does.
        login(username, passphrase.substring(0, 72) + " but a different ending").andExpect(status().isUnauthorized());
    }

    @Test
    void anOldBcryptPasswordIsUpgradedAtSignInWithoutSigningOutOtherDevices() throws Exception {
        Session user = register();
        User account = userRepository.findById(user.id()).orElseThrow();
        // As stored by earlier versions: unprefixed bcrypt.
        account.setPassword(new BCryptPasswordEncoder().encode(PASSWORD));
        userRepository.save(account);

        login(user.username(), PASSWORD).andExpect(status().isOk());

        assertThat(userRepository.findById(user.id()).orElseThrow().getPassword()).startsWith("{argon2}");
        mvc.perform(get("/api/v1/users/me").cookie(user.cookie())).andExpect(status().isOk());
        login(user.username(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void sessionCookiesAreSecureWhenTheRequestCameOverHttps() throws Exception {
        Session user = register();
        String overHttps = mvc.perform(post("/api/v1/auth/login").secure(true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", user.username(), "password", PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("Set-Cookie");
        String overHttp = login(user.username(), PASSWORD).andReturn().getResponse().getHeader("Set-Cookie");

        assertThat(overHttps).contains("Secure").contains("HttpOnly").contains("SameSite=Lax");
        assertThat(overHttp).doesNotContain("Secure").contains("HttpOnly");
    }

    // ── Disabled accounts ────────────────────────────────────────────────────

    @Test
    void onlyTheRightPasswordRevealsThatAnAccountIsDisabled() throws Exception {
        Session admin = admin();
        Session user = register();
        mvc.perform(patch("/api/v1/admin/users/{id}", user.id()).cookie(admin.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("enabled", false)))
                .andExpect(status().isOk());

        login(user.username(), "a-wrong-guess")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
        login(user.username(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("This account has been disabled"));
    }

    // ── Sessions ─────────────────────────────────────────────────────────────

    @Test
    void signingOutEndsTheSessionOnTheServer() throws Exception {
        Session user = register();
        Cookie copy = new Cookie(user.cookie().getName(), user.cookie().getValue());

        mvc.perform(post("/api/v1/auth/logout").cookie(user.cookie())).andExpect(status().isNoContent());

        // A copy of the cookie (another tab, or a stolen one) no longer works.
        mvc.perform(get("/api/v1/users/me").cookie(copy)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + copy.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signingOutOtherDevicesKeepsThisOne() throws Exception {
        Session laptop = register();
        Cookie phone = login(laptop.username(), PASSWORD).andReturn().getResponse().getCookie(SESSION_COOKIE);
        Thread.sleep(1100); // session times have one-second precision

        MvcResult result = mvc.perform(post("/api/v1/users/me/sessions/sign-out-others")
                        .cookie(laptop.cookie()).with(csrf()))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie renewed = result.getResponse().getCookie(SESSION_COOKIE);

        mvc.perform(get("/api/v1/users/me").cookie(phone)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").cookie(laptop.cookie())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").cookie(renewed)).andExpect(status().isOk());
    }

    // ── Cross-site requests ──────────────────────────────────────────────────

    @Test
    void anotherSiteCantSignSomeoneIn() throws Exception {
        Session user = register();
        mvc.perform(post("/api/v1/auth/login").header("Sec-Fetch-Site", "cross-site")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", user.username(), "password", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("This request came from another website, so it was blocked"));
        mvc.perform(post("/api/v1/auth/login").header("Origin", "https://evil.example")
                        .header("Host", "silo.home")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", user.username(), "password", PASSWORD)))
                .andExpect(status().isForbidden());

        // HomeSilo's own pages, and clients without a browser, are fine.
        mvc.perform(post("/api/v1/auth/login").header("Sec-Fetch-Site", "same-origin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", user.username(), "password", PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").header("Origin", "https://silo.home").header("Host", "silo.home")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", user.username(), "password", PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void anotherSiteCantChangeFilesEvenWithACookie() throws Exception {
        Session user = register();
        mvc.perform(post("/api/v1/folders").cookie(user.cookie()).with(csrf())
                        .header("Sec-Fetch-Site", "same-site")
                        .contentType(MediaType.APPLICATION_JSON).content(json("name", "From a sibling site")))
                .andExpect(status().isForbidden());
        // Reading is unaffected: links from elsewhere still open files.
        mvc.perform(get("/api/v1/users/me").cookie(user.cookie()).header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isOk());
    }

    // ── Downloads ────────────────────────────────────────────────────────────

    @Test
    void downloadsAreSandboxed() throws Exception {
        Session user = register();
        String fileId = upload(user, "page.html", "<script>alert(1)</script>", null);
        mvc.perform(get("/api/v1/files/{id}/download", fileId).cookie(user.cookie()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Content-Security-Policy"))
                        .isEqualTo("sandbox; default-src 'none'"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Content-Disposition"))
                        .startsWith("attachment"));
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password)
            throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("username", username, "password", password)));
    }
}
