package com.andrija.homesiloserver.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param fileName the file's name on the uploader's computer
 * @param size     its exact size in bytes
 * @param folderId where it goes; empty for the top of My Files
 */
public record StartUploadRequest(
        @NotBlank(message = "The file needs a name")
        @Size(max = 1024, message = "That file name is too long")
        String fileName,
        @Positive(message = "Empty files are uploaded in one request")
        long size,
        UUID folderId
) {}
