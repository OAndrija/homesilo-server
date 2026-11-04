package com.andrija.homesiloserver.folder.dto;

import com.andrija.homesiloserver.common.web.ItemName;

import java.util.UUID;

/** {@code parentId} is optional; {@code null} creates a top-level folder. */
public record CreateFolderRequest(
        @ItemName String name,
        UUID parentId
) {}
