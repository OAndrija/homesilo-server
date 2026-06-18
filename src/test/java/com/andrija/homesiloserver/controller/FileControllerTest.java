package com.andrija.homesiloserver.controller;

import com.andrija.homesiloserver.constant.UserRole;
import com.andrija.homesiloserver.dto.FileMetadataResponse;
import com.andrija.homesiloserver.entity.User;
import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    private Authentication mockAuthContext;
    private UUID testUserId;

    @BeforeEach
    void setupUserSecurityContext() {
        testUserId = UUID.randomUUID();
        User domainUser = User.builder()
                .id(testUserId)
                .username("andrija_user")
                .email("user@homesilo.local")
                .role(UserRole.USER)
                .enabled(true)
                .build();

        ServerUserDetails principal = new ServerUserDetails(domainUser);
        mockAuthContext = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    @DisplayName("POST /api/v1/files/upload - Success Flow with Custom Security Principal")
    void uploadFile_ShouldReturnCreated_WhenAuthenticatedUserUploads() throws Exception {
        MockMultipartFile mockMultipartFile = new MockMultipartFile(
                "file", "vacation_photo.png", MediaType.IMAGE_PNG_VALUE, "fake-image-bytes".getBytes()
        );

        FileMetadataResponse expectedResponse = new FileMetadataResponse(
                UUID.randomUUID(), "vacation_photo.png", MediaType.IMAGE_PNG_VALUE, 16L, false, LocalDateTime.now(), LocalDateTime.now()
        );

        when(fileService.upload(any(), eq(testUserId))).thenReturn(expectedResponse);

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(mockMultipartFile)
                        .with(authentication(mockAuthContext))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFileName").value("vacation_photo.png"))
                .andExpect(jsonPath("$.contentType").value(MediaType.IMAGE_PNG_VALUE))
                .andExpect(jsonPath("$.size").value(16))
                .andExpect(jsonPath("$.trashed").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/files/download/{id} - Verifies Streaming Headers Structure")
    void downloadFile_ShouldStreamBinaryContentWithAttachmentHeaders() throws Exception {
        UUID mockFileId = UUID.randomUUID();
        Resource mockResource = new ByteArrayResource("raw-file-stream-content".getBytes());
        FileMetadataResponse mockMetadata = new FileMetadataResponse(
                mockFileId, "document.pdf", MediaType.APPLICATION_PDF_VALUE, 23L, false, LocalDateTime.now(), LocalDateTime.now()
        );

        when(fileService.download(mockFileId, testUserId)).thenReturn(mockResource);
        when(fileService.getFileMetadata(mockFileId, testUserId)).thenReturn(mockMetadata);

        mockMvc.perform(get("/api/v1/files/{fileId}/download", mockFileId)
                        .with(authentication(mockAuthContext)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("filename=\"document.pdf\"")
                ))
                .andExpect(content().string("raw-file-stream-content"));
    }

    @Test
    @DisplayName("DELETE /api/v1/files/{id} - Success Flow")
    void deleteFile_ShouldReturnOk_WhenPermanentlyDeleted() throws Exception {
        UUID mockFileId = UUID.randomUUID();
        doNothing().when(fileService).deletePermanently(mockFileId, testUserId);

        mockMvc.perform(delete("/api/v1/files/{fileId}", mockFileId)
                        .with(authentication(mockAuthContext))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }
}