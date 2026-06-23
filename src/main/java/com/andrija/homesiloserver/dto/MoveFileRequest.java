package com.andrija.homesiloserver.dto;

import java.util.UUID;

public record MoveFileRequest(
        UUID targetFolderId
) {
}
