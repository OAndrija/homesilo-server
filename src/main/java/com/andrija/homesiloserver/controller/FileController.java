package com.andrija.homesiloserver.controller;

import com.andrija.homesiloserver.dto.FileMetadataResponse;
import com.andrija.homesiloserver.dto.PageResponse;
import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {
    private final FileService fileService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileMetadataResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal ServerUserDetails userDetails
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fileService.upload(file, userDetails.getId()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<FileMetadataResponse>> listActiveFiles(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal ServerUserDetails userDetails
    ) {
        return ResponseEntity.ok(fileService.listFiles(userDetails.getId(), pageable));
    }

    @GetMapping("/trash")
    public ResponseEntity<PageResponse<FileMetadataResponse>> listTrashedFiles(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal ServerUserDetails userDetails
    ) {
        return ResponseEntity.ok(fileService.listTrashedFiles(userDetails.getId(), pageable));
    }

    @GetMapping("/{fileId}/metadata")
    public ResponseEntity<FileMetadataResponse> getMetadata(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal ServerUserDetails userDetails
    ) {
        return ResponseEntity.ok(fileService.getFileMetadata(fileId, userDetails.getId()));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal ServerUserDetails userDetails
    ) {
        Resource fileResource = fileService.download(fileId, userDetails.getId());
        FileMetadataResponse metadata = fileService.getFileMetadata(fileId, userDetails.getId());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + metadata.originalFileName() + "\"")
                .body(fileResource);
    }

    @PatchMapping("/{fileId}/trash")
    public ResponseEntity<FileMetadataResponse> moveToTrash(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal ServerUserDetails userDetails
    ) {
        return ResponseEntity.ok(fileService.trashFile(fileId, userDetails.getId()));
    }

    @PatchMapping("/{fileId}/restore")
    public ResponseEntity<FileMetadataResponse> restoreFile(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal ServerUserDetails userDetails
    ) {
        return ResponseEntity.ok(fileService.restore(fileId, userDetails.getId()));
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal ServerUserDetails userDetails
    ) {
        fileService.deletePermanently(fileId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
