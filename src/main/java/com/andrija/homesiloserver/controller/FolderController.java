package com.andrija.homesiloserver.controller;

import com.andrija.homesiloserver.dto.*;
import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.service.FolderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/folders")
@RequiredArgsConstructor
public class FolderController {
    private final FolderService folderService;

    // ── Root contents ─────────────────────────────────────────────────────────

    /**
     * GET /api/v1/folders/root
     * Returns top-level subfolders + top-level files (files with no parent folder).
     */
    @GetMapping("/root")
    public ResponseEntity<FolderContentsResponse> getRootContents(
            @PageableDefault(size = 20, sort = "lastModified", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal ServerUserDetails userDetails
    ) {
        return ResponseEntity.ok(folderService.getRootContents(userDetails.getId(), pageable));
    }

    // ── Trash ─────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/folders/trash
     * Returns top-level trashed folders (folders whose parent is not also trashed).
     * Must be declared before /{folderId} so "trash" isn't parsed as a UUID.
     */
    @GetMapping("/trash")
    public ResponseEntity<PageResponse<FolderResponse>> listTrashedFolders(
            @PageableDefault(size = 20, sort = "trashedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal ServerUserDetails userDetails
    ) {
        return ResponseEntity.ok(folderService.listTrashedFolders(userDetails.getId(), pageable));
    }

    // ── Folder CRUD ───────────────────────────────────────────────────────────

    /**
     * POST /api/v1/folders
     * Creates a new folder. parentId in the request body is optional (null = root level).
     */
    @PostMapping
    public ResponseEntity<FolderResponse> createFolder(
            @Valid @RequestBody CreateFolderRequest request,
            @AuthenticationPrincipal ServerUserDetails userDetails
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(folderService.createFolder(request, userDetails.getId()));
    }

    /**
     * GET /api/v1/folders/{folderId}
     * Returns the folder metadata (not its contents).
     */
    @GetMapping("/{folderId}")
    public ResponseEntity<FolderResponse> getFolder(
            @PathVariable UUID folderId,
            @AuthenticationPrincipal ServerUserDetails userDetails
    ) {
        return ResponseEntity.ok(folderService.getFolder(folderId, userDetails.getId()));
    }

    /**
     * GET /api/v1/folders/{folderId}/contents
     * Returns the folder's direct contents: subfolders + paginated files.
     */
    @GetMapping("/{folderId}/contents")
    public ResponseEntity<FolderContentsResponse> getFolderContents(
            @PathVariable UUID folderId,
            @PageableDefault(size = 20, sort = "lastModified", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal ServerUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                folderService.getFolderContents(folderId, userDetails.getId(), pageable)
        );
    }

    /**
     * PATCH /api/v1/folders/{folderId}/rename
     */
    @PatchMapping("/{folderId}/rename")
    public ResponseEntity<FolderResponse> renameFolder(
            @PathVariable UUID folderId,
            @Valid @RequestBody RenameFolderRequest request,
            @AuthenticationPrincipal ServerUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                folderService.renameFolder(folderId, request, userDetails.getId())
        );
    }

    /**
     * PATCH /api/v1/folders/{folderId}/move
     * Moves the folder to a new parent. targetParentId null = move to root.
     */
    @PatchMapping("/{folderId}/move")
    public ResponseEntity<FolderResponse> moveFolder(
            @PathVariable UUID folderId,
            @RequestBody MoveFolderRequest request,
            @AuthenticationPrincipal ServerUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                folderService.moveFolder(folderId, request, userDetails.getId())
        );
    }

    // ── Trash lifecycle ───────────────────────────────────────────────────────

    /**
     * PATCH /api/v1/folders/{folderId}/trash
     * Moves the folder and all its contents to trash recursively.
     */
    @PatchMapping("/{folderId}/trash")
    public ResponseEntity<FolderResponse> trashFolder(
            @PathVariable UUID folderId,
            @AuthenticationPrincipal ServerUserDetails userDetails
    ) {
        return ResponseEntity.ok(folderService.trashFolder(folderId, userDetails.getId()));
    }

    /**
     * PATCH /api/v1/folders/{folderId}/restore
     * Restores the folder and all its contents recursively.
     */
    @PatchMapping("/{folderId}/restore")
    public ResponseEntity<FolderResponse> restoreFolder(
            @PathVariable UUID folderId,
            @AuthenticationPrincipal ServerUserDetails userDetails
    ) {
        return ResponseEntity.ok(folderService.restoreFolder(folderId, userDetails.getId()));
    }

    /**
     * DELETE /api/v1/folders/{folderId}
     * Permanently deletes the folder, all its subfolders, and all their files.
     * The folder must already be trashed.
     */
    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> deleteFolder(
            @PathVariable UUID folderId,
            @AuthenticationPrincipal ServerUserDetails userDetails
    ) {
        folderService.deleteFolder(folderId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}