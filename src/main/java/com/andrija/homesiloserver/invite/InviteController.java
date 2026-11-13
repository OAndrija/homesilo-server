package com.andrija.homesiloserver.invite;

import com.andrija.homesiloserver.invite.dto.CreateInviteRequest;
import com.andrija.homesiloserver.invite.dto.CreatedInviteResponse;
import com.andrija.homesiloserver.invite.dto.InviteResponse;
import com.andrija.homesiloserver.security.ServerUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Admins' invite links (admin-only by the /api/v1/admin/** rule). */
@RestController
@RequestMapping("/api/v1/admin/invites")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService service;

    @GetMapping
    public List<InviteResponse> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<CreatedInviteResponse> create(
            @Valid @RequestBody CreateInviteRequest request,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(principal.getId(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        service.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
