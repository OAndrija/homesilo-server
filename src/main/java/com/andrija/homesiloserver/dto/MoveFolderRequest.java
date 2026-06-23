package com.andrija.homesiloserver.dto;

import java.util.UUID;

public record MoveFolderRequest(
        UUID targetParentId
) {
}
