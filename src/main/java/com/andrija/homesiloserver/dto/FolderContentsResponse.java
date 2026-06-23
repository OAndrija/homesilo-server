package com.andrija.homesiloserver.dto;

import java.util.List;

public record FolderContentsResponse(
        FolderResponse folder,
        List<FolderResponse> breadcrumb,
        List<FolderResponse> subfolders,
        PageResponse<FileMetadataResponse> files
) {
}
