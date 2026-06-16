package com.andrija.homesiloserver.controller;

import com.andrija.homesiloserver.constant.UserRole;
import com.andrija.homesiloserver.entity.User;
import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private Authentication buildMockAuthentication(UserRole assignedRole) {
        User domainUser = User.builder()
                .id(UUID.randomUUID())
                .username("test_subject")
                .role(assignedRole)
                .enabled(true)
                .build();
        ServerUserDetails principal = new ServerUserDetails(domainUser);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    @DisplayName("GET /api/v1/users - Blocks access when user lacks ROLE_ADMIN")
    void listAllUsers_ShouldReturnForbidden_WhenUserIsStandardRole() throws Exception {
        Authentication plainUserAuth = buildMockAuthentication(UserRole.USER);

        mockMvc.perform(get("/api/v1/users")
                        .with(authentication(plainUserAuth)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/users - Grants access when user possesses ROLE_ADMIN")
    void listAllUsers_ShouldReturnOk_WhenUserIsAdminRole() throws Exception {
        Authentication adminUserAuth = buildMockAuthentication(UserRole.ADMIN);

        mockMvc.perform(get("/api/v1/users")
                        .with(authentication(adminUserAuth)))
                .andExpect(status().isOk());
    }
}