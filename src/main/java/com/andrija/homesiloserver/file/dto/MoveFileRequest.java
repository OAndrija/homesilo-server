package com.andrija.homesiloserver.file.dto;

import java.util.UUID;

/** {@code targetFolderId} of {@code null} moves the file to the top level. */
public record MoveFileRequest(UUID targetFolderId) {}
