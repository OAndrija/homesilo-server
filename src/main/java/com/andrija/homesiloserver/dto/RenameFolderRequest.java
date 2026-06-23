package com.andrija.homesiloserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameFolderRequest(
        @NotBlank(message = "Folder name must not be blank")
        @Size(max = 255, message = "Folder name must not exceed 255 characters")
        String name
) {
}
