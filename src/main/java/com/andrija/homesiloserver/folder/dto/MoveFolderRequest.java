package com.andrija.homesiloserver.folder.dto;

import java.util.UUID;

/** {@code targetParentId} of {@code null} moves the folder to the top level. */
public record MoveFolderRequest(UUID targetParentId) {}
