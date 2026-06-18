package com.andrija.homesiloserver.controller;

import com.andrija.homesiloserver.dto.AuthResponse;
import com.andrija.homesiloserver.dto.UserLoginRequest;
import com.andrija.homesiloserver.dto.UserRegisterRequest;
import com.andrija.homesiloserver.service.AuthenticationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthenticationService authenticationService;

    @Test
    @DisplayName("POST /api/v1/auth/register - Success Flow")
    void register_ShouldReturnCreated_WhenPayloadIsValid() throws Exception {
        UserRegisterRequest request = new UserRegisterRequest("tester", "andrija@example.com", "securePassword123");
        AuthResponse mockResponse = new AuthResponse("mocked-jwt-token-string", 3600L);

        when(authenticationService.register(any(UserRegisterRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("mocked-jwt-token-string"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - Fail Flow (Validation Blank Fields)")
    void login_ShouldReturnBadRequest_WhenPayloadIsMalformed() throws Exception {
        UserLoginRequest invalidRequest = new UserLoginRequest("", ""); // Triggers @Valid validation failures

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}