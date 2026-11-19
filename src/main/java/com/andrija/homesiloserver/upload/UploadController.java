package com.andrija.homesiloserver.upload;

import com.andrija.homesiloserver.file.dto.FileMetadataResponse;
import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.upload.dto.StartUploadRequest;
import com.andrija.homesiloserver.upload.dto.UploadSessionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

/** Chunked uploads, for files too big for one request. See {@link UploadService}. */
@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploads;

    @PostMapping
    public ResponseEntity<UploadSessionResponse> start(
            @Valid @RequestBody StartUploadRequest request,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(uploads.start(principal.getId(), request));
    }

    @GetMapping("/{id}")
    public UploadSessionResponse status(@PathVariable UUID id, @AuthenticationPrincipal ServerUserDetails principal) {
        return uploads.status(principal.getId(), id);
    }

    /** One chunk, as the raw bytes of the request body. */
    @PutMapping(value = "/{id}/chunks/{index}", consumes = "application/octet-stream")
    public ResponseEntity<Void> putChunk(
            @PathVariable UUID id,
            @PathVariable int index,
            HttpServletRequest request,
            @AuthenticationPrincipal ServerUserDetails principal
    ) throws IOException {
        uploads.putChunk(principal.getId(), id, index, request.getContentLengthLong(), request.getInputStream());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<FileMetadataResponse> complete(
            @PathVariable UUID id,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(uploads.complete(principal.getId(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable UUID id, @AuthenticationPrincipal ServerUserDetails principal) {
        uploads.cancel(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
