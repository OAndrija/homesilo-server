package com.andrija.homesiloserver.file;

import com.andrija.homesiloserver.common.exception.NotFoundException;
import com.andrija.homesiloserver.common.web.PageResponse;
import com.andrija.homesiloserver.common.web.RenameRequest;
import com.andrija.homesiloserver.file.ArchiveService.ArchivePlan;
import com.andrija.homesiloserver.file.FileService.FileContent;
import com.andrija.homesiloserver.file.dto.FileMetadataResponse;
import com.andrija.homesiloserver.file.dto.MoveFileRequest;
import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.thumbnail.ThumbnailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    /** Served inline as-is. Anything script-capable (HTML, SVG, XML, JS) is downgraded to plain text. */
    private static final List<String> INLINE_TYPE_PREFIXES = List.of("image/", "video/", "audio/");
    private static final List<String> TEXT_LIKE_TYPES = List.of(
            "application/json", "application/xml", "application/javascript", "application/x-sh", "application/x-yaml");
    private static final String PDF = "application/pdf";
    private static final String SVG = "image/svg+xml";

    private final FileService fileService;
    private final ArchiveService archiveService;
    private final ThumbnailService thumbnailService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileMetadataResponse> uploadFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) UUID folderId,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fileService.upload(file, principal.getId(), folderId));
    }

    // ── Listings ─────────────────────────────────────────────────────────────

    @GetMapping
    public PageResponse<FileMetadataResponse> listActiveFiles(
            @PageableDefault(size = 50, sort = "lastModified", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return fileService.listActive(principal.getId(), pageable);
    }

    @GetMapping("/recent")
    public PageResponse<FileMetadataResponse> listRecentFiles(
            @PageableDefault(size = 10, sort = "uploadedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return fileService.listActive(principal.getId(), pageable);
    }

    /** The newest pictures first, from every folder; for the dashboard and, later, a photos view. */
    @GetMapping("/photos")
    public PageResponse<FileMetadataResponse> listPhotos(
            @PageableDefault(size = 24, sort = "uploadedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return fileService.listPhotos(principal.getId(), pageable);
    }

    @GetMapping("/starred")
    public PageResponse<FileMetadataResponse> listStarredFiles(
            @PageableDefault(size = 50, sort = "lastModified", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return fileService.listStarred(principal.getId(), pageable);
    }

    @GetMapping("/trash")
    public PageResponse<FileMetadataResponse> listTrashedFiles(
            @PageableDefault(size = 50, sort = "trashedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return fileService.listTrashed(principal.getId(), pageable);
    }

    @GetMapping("/search")
    public PageResponse<FileMetadataResponse> searchFiles(
            @RequestParam @NotBlank @Size(max = 255) String query,
            @PageableDefault(size = 50, sort = "lastModified", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return fileService.searchActive(principal.getId(), query, pageable);
    }

    @GetMapping("/starred/search")
    public PageResponse<FileMetadataResponse> searchStarredFiles(
            @RequestParam @NotBlank @Size(max = 255) String query,
            @PageableDefault(size = 50, sort = "lastModified", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return fileService.searchStarred(principal.getId(), query, pageable);
    }

    @GetMapping("/trash/search")
    public PageResponse<FileMetadataResponse> searchTrashedFiles(
            @RequestParam @NotBlank @Size(max = 255) String query,
            @PageableDefault(size = 50, sort = "trashedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return fileService.searchTrashed(principal.getId(), query, pageable);
    }

    // ── Content ──────────────────────────────────────────────────────────────

    @GetMapping("/{fileId}/metadata")
    public FileMetadataResponse getMetadata(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return fileService.getMetadata(fileId, principal.getId());
    }

    /** Streams the file as an attachment. Supports HTTP range requests (resumable downloads). */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        FileContent content = fileService.getContent(fileId, principal.getId());
        return ResponseEntity.ok()
                .contentType(parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(ContentDisposition.attachment(), content.fileName()))
                .body(content.resource());
    }

    /**
     * Opens the file in the browser. Media and PDFs are served as-is (with range
     * support, so videos can seek); text-like content is forced to
     * {@code text/plain} and every response is sandboxed by CSP, so an uploaded
     * HTML or SVG file can never run script in HomeSilo's origin.
     */
    @GetMapping("/{fileId}/preview")
    public ResponseEntity<Resource> previewFile(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        FileContent content = fileService.getContent(fileId, principal.getId());
        String type = content.contentType().toLowerCase(Locale.ROOT);

        MediaType servedAs;
        ContentDisposition.Builder disposition = ContentDisposition.inline();
        if (type.equals(PDF) || (INLINE_TYPE_PREFIXES.stream().anyMatch(type::startsWith) && !type.equals(SVG))) {
            servedAs = parseMediaType(content.contentType());
        } else if (type.startsWith("text/") || type.equals(SVG) || TEXT_LIKE_TYPES.contains(type)) {
            servedAs = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);
        } else {
            servedAs = MediaType.APPLICATION_OCTET_STREAM;
            disposition = ContentDisposition.attachment();
        }

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(servedAs)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(disposition, content.fileName()));
        if (type.equals(PDF)) {
            // Chrome's built-in PDF viewer refuses to run inside a sandboxed document, so
            // PDFs get no sandbox; instead they may be framed, but only by HomeSilo itself
            // (the client's in-app viewer). SecurityConfig only defaults to DENY.
            response.header("X-Frame-Options", "SAMEORIGIN");
            response.header("Content-Security-Policy", "frame-ancestors 'self'");
        } else {
            response.header("Content-Security-Policy", "sandbox; default-src 'none'; img-src 'self'; media-src 'self'; style-src 'unsafe-inline'");
        }
        return response.body(content.resource());
    }

    /**
     * A small preview of an image (at most {@value ThumbnailService#SIZE}px on its longest
     * edge), made on first request and cached. 404 when there is none; the file's
     * {@code thumbnail} flag says in advance. A file's bytes never change, so browsers
     * may keep it for a month.
     */
    @GetMapping("/{fileId}/thumbnail")
    public ResponseEntity<Resource> thumbnail(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        FileService.ThumbnailSource source = fileService.getThumbnailSource(fileId, principal.getId());
        return thumbnailService.get(source.ownerId(), source.hash(), source.contentType(), source.size())
                .<ResponseEntity<Resource>>map(thumb -> ResponseEntity.ok()
                        .contentType(thumb.type())
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                        .eTag(source.hash())
                        .header("Content-Security-Policy", "sandbox; default-src 'none'")
                        .body(new FileSystemResource(thumb.path())))
                .orElseThrow(() -> new NotFoundException("No preview for this file"));
    }

    /** Streams a zip of the selected files and folders (folders are included recursively). */
    @GetMapping("/download-zip")
    public ResponseEntity<StreamingResponseBody> downloadAsZip(
            @RequestParam(name = "fileIds", required = false) List<UUID> fileIds,
            @RequestParam(name = "folderIds", required = false) List<UUID> folderIds,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        ArchivePlan plan = archiveService.plan(
                fileIds != null ? fileIds : List.of(),
                folderIds != null ? folderIds : List.of(),
                principal.getId());
        StreamingResponseBody body = out -> archiveService.write(plan, out);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(ContentDisposition.attachment(), plan.fileName()))
                .body(body);
    }

    // ── Mutations ────────────────────────────────────────────────────────────

    @PatchMapping("/{fileId}/rename")
    public FileMetadataResponse renameFile(
            @PathVariable UUID fileId,
            @Valid @RequestBody RenameRequest request,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return fileService.rename(fileId, request.name(), principal.getId());
    }

    @PatchMapping("/{fileId}/move")
    public FileMetadataResponse moveFile(
            @PathVariable UUID fileId,
            @RequestBody MoveFileRequest request,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return fileService.move(fileId, request.targetFolderId(), principal.getId());
    }

    @PatchMapping("/{fileId}/star")
    public FileMetadataResponse toggleStar(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return fileService.toggleStar(fileId, principal.getId());
    }

    @PatchMapping("/{fileId}/trash")
    public FileMetadataResponse moveToTrash(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return fileService.trash(fileId, principal.getId());
    }

    @PatchMapping("/{fileId}/restore")
    public FileMetadataResponse restoreFile(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return fileService.restore(fileId, principal.getId());
    }

    /** Permanently deletes a trashed file. */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        fileService.deletePermanently(fileId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    private static String disposition(ContentDisposition.Builder builder, String fileName) {
        return builder.filename(fileName, StandardCharsets.UTF_8).build().toString();
    }

    private static MediaType parseMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
