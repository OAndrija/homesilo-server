package com.andrija.homesiloserver.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A new server needs its admin, made with the setup code, before anyone can sign
 * up. Tests that just need accounts call this first; it does nothing once done.
 */
public final class FirstAdmin {

    /** Matches homesilo.auth.setup-code in the test application.properties. */
    public static final String SETUP_CODE = "TEST-SETUP-CODE-0001";
    public static final String USERNAME = "first-admin";
    public static final String PASSWORD = "first-admin-password";

    private FirstAdmin() {
    }

    public static void ensureSetUp(MockMvc mvc) {
        try {
            String config = mvc.perform(get("/api/v1/auth/config")).andReturn()
                    .getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (!Boolean.TRUE.equals(JsonPath.read(config, "$.setupRequired"))) return;
            int status = mvc.perform(post("/api/v1/auth/setup").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"setupCode":"%s","username":"%s","email":"%s@example.com","password":"%s"}
                                    """.formatted(SETUP_CODE, USERNAME, USERNAME, PASSWORD)))
                    .andReturn().getResponse().getStatus();
            if (status != 201 && status != 409) throw new IllegalStateException("Setup failed with " + status);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
