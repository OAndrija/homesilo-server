package com.andrija.homesiloserver.admin;

import com.andrija.homesiloserver.admin.dto.AdminOverviewResponse;
import com.andrija.homesiloserver.admin.dto.AdminUserResponse;
import com.andrija.homesiloserver.admin.dto.CreateUserRequest;
import com.andrija.homesiloserver.admin.dto.ResetPasswordRequest;
import com.andrija.homesiloserver.admin.dto.UpdateUserRequest;
import com.andrija.homesiloserver.common.web.PageResponse;
import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.user.UserRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Account management. Admins only (also enforced for the whole path in SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUserService adminUserService;

    @GetMapping("/overview")
    public AdminOverviewResponse overview() {
        return adminUserService.overview();
    }

    /** Users matching every filter given; {@code query} matches username or email. */
    @GetMapping("/users")
    public PageResponse<AdminUserResponse> listUsers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) AccountStatus status,
            @PageableDefault(size = 25, sort = "username", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return adminUserService.list(query, role, status, pageable);
    }

    @GetMapping("/users/{id}")
    public AdminUserResponse getUser(@PathVariable UUID id) {
        return adminUserService.get(id);
    }

    @PostMapping("/users")
    public ResponseEntity<AdminUserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminUserService.create(principal.getId(), request));
    }

    @PatchMapping("/users/{id}")
    public AdminUserResponse updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return adminUserService.update(principal.getId(), id, request);
    }

    @PutMapping("/users/{id}/password")
    public ResponseEntity<Void> resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody ResetPasswordRequest request,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        adminUserService.resetPassword(principal.getId(), id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        adminUserService.delete(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
