package com.andrija.homesiloserver.folder.dto;

import com.andrija.homesiloserver.common.web.PageResponse;
import com.andrija.homesiloserver.file.dto.FileMetadataResponse;

import java.util.List;

/**
 * @param folder     the folder being viewed, or {@code null} at the root
 * @param breadcrumb path from the top level down to (and including) {@code folder}
 */
public record FolderContentsResponse(
        FolderResponse folder,
        List<FolderResponse> breadcrumb,
        List<FolderResponse> subfolders,
        PageResponse<FileMetadataResponse> files
) {}
