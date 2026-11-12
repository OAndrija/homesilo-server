package com.andrija.homesiloserver.folder;

import com.andrija.homesiloserver.common.web.PageResponse;
import com.andrija.homesiloserver.common.web.RenameRequest;
import com.andrija.homesiloserver.folder.dto.CreateFolderRequest;
import com.andrija.homesiloserver.folder.dto.FolderContentsResponse;
import com.andrija.homesiloserver.folder.dto.FolderResponse;
import com.andrija.homesiloserver.folder.dto.MoveFolderRequest;
import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.trash.TrashService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;
    private final TrashService trashService;

    /** Top-level folders plus a page of top-level files. */
    @GetMapping("/root")
    public FolderContentsResponse getRootContents(
            @PageableDefault(size = 50, sort = "lastModified", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return folderService.getRootContents(principal.getId(), pageable);
    }

    /** Trashed folders whose parent isn't also trashed. */
    @GetMapping("/trash")
    public PageResponse<FolderResponse> listTrashedFolders(
            @PageableDefault(size = 50, sort = "trashedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return folderService.listTrashed(principal.getId(), pageable);
    }

    /** Starred folders (not in trash), by name; {@code query} narrows them. */
    @GetMapping("/starred")
    public List<FolderResponse> listStarredFolders(
            @RequestParam(required = false) String query,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return folderService.listStarred(principal.getId(), query);
    }

    @GetMapping("/search")
    public List<FolderResponse> searchFolders(
            @RequestParam @NotBlank String query,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return folderService.search(principal.getId(), query);
    }

    @PostMapping
    public ResponseEntity<FolderResponse> createFolder(
            @Valid @RequestBody CreateFolderRequest request,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(folderService.create(request, principal.getId()));
    }

    @GetMapping("/{folderId}")
    public FolderResponse getFolder(
            @PathVariable UUID folderId,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return folderService.getFolder(folderId, principal.getId());
    }

    /** Direct subfolders plus a page of the folder's files, with the breadcrumb trail. */
    @GetMapping("/{folderId}/contents")
    public FolderContentsResponse getFolderContents(
            @PathVariable UUID folderId,
            @PageableDefault(size = 50, sort = "lastModified", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return folderService.getContents(folderId, principal.getId(), pageable);
    }

    @PatchMapping("/{folderId}/rename")
    public FolderResponse renameFolder(
            @PathVariable UUID folderId,
            @Valid @RequestBody RenameRequest request,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return folderService.rename(folderId, request.name(), principal.getId());
    }

    @PatchMapping("/{folderId}/move")
    public FolderResponse moveFolder(
            @PathVariable UUID folderId,
            @RequestBody MoveFolderRequest request,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return folderService.move(folderId, request.targetParentId(), principal.getId());
    }

    @PatchMapping("/{folderId}/star")
    public FolderResponse toggleStar(
            @PathVariable UUID folderId,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return folderService.toggleStar(folderId, principal.getId());
    }

    @PatchMapping("/{folderId}/trash")
    public FolderResponse trashFolder(
            @PathVariable UUID folderId,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return folderService.trash(folderId, principal.getId());
    }

    @PatchMapping("/{folderId}/restore")
    public FolderResponse restoreFolder(
            @PathVariable UUID folderId,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return folderService.restore(folderId, principal.getId());
    }

    /** Permanently deletes a trashed folder with everything inside it. */
    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> deleteFolder(
            @PathVariable UUID folderId,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        trashService.deleteFolder(folderId, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
