package com.andrija.homesiloserver.auth;

import com.andrija.homesiloserver.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIntegrationTest extends IntegrationTest {

    @Test
    void registerSetsHttpOnlySessionCookieAndNeverReturnsTheToken() throws Exception {
        String username = "cookie-" + System.nanoTime();
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", username, "email", username + "@Example.com", "password", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(jsonPath("$.user.username").value(username))
                .andExpect(jsonPath("$.user.email").value(username.toLowerCase() + "@example.com"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void sessionCookieAuthenticatesAndMissingSessionIsAJson401() throws Exception {
        Session session = register();

        mvc.perform(get("/api/v1/users/me").cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(session.username()));

        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/problem+json")))
                .andExpect(jsonPath("$.status").value(401));

        mvc.perform(get("/api/v1/users/me").cookie(new Cookie(SESSION_COOKIE, "not-a-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateUsernameAndEmailAreConflicts() throws Exception {
        Session existing = register();
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", existing.username().toUpperCase(), "email", "other@example.com",
                                "password", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Username is already taken"));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", "fresh-" + System.nanoTime(), "email", existing.username() + "@example.com",
                                "password", PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", "x", "email", "not-an-email", "password", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void loginAcceptsUsernameOrEmail() throws Exception {
        Session session = register();
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", session.username(), "password", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(session.id().toString()));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", session.username() + "@example.com", "password", PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void repeatedFailedLoginsAreThrottled() throws Exception {
        Session session = register();
        String wrong = json("username", session.username(), "password", "wrong-password");

        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", session.username(), "password", PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void logoutClearsTheCookie() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie cleared = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).isZero();
    }

    @Test
    void changingPasswordRevokesOlderSessions() throws Exception {
        Session session = register();
        Thread.sleep(1100); // JWT iat has one-second resolution

        MvcResult result = mvc.perform(put("/api/v1/users/me/password")
                        .cookie(session.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("currentPassword", PASSWORD, "newPassword", "an-even-better-password")))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie renewed = result.getResponse().getCookie(SESSION_COOKIE);

        mvc.perform(get("/api/v1/users/me").cookie(session.cookie())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").cookie(renewed)).andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPasswordIsRejected() throws Exception {
        Session session = register();
        mvc.perform(put("/api/v1/users/me/password")
                        .cookie(session.cookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("currentPassword", "nope-nope-nope", "newPassword", "an-even-better-password")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cookieAuthenticatedMutationsRequireCsrfToken() throws Exception {
        Session session = register();
        mvc.perform(post("/api/v1/folders")
                        .cookie(session.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("name", "no-csrf")))
                .andExpect(status().isForbidden());
    }

    @Test
    void regularUsersCannotUseAdminEndpoints() throws Exception {
        Session session = register();
        mvc.perform(get("/api/v1/admin/users").cookie(session.cookie())).andExpect(status().isForbidden());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
