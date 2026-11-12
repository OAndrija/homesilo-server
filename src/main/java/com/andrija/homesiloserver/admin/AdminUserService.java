package com.andrija.homesiloserver.admin;

import com.andrija.homesiloserver.activity.ActivityAction;
import com.andrija.homesiloserver.activity.ActivityRecorder;
import com.andrija.homesiloserver.activity.TargetType;
import com.andrija.homesiloserver.admin.dto.AdminOverviewResponse;
import com.andrija.homesiloserver.admin.dto.AdminUserResponse;
import com.andrija.homesiloserver.admin.dto.CreateUserRequest;
import com.andrija.homesiloserver.admin.dto.ResetPasswordRequest;
import com.andrija.homesiloserver.admin.dto.UpdateUserRequest;
import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.common.exception.BadRequestException;
import com.andrija.homesiloserver.common.exception.ConflictException;
import com.andrija.homesiloserver.common.web.PageResponse;
import com.andrija.homesiloserver.file.FileMetadataRepository.OwnerUsage;
import com.andrija.homesiloserver.file.FileMetadataRepository;
import com.andrija.homesiloserver.folder.FolderRepository;
import com.andrija.homesiloserver.storage.BlobsReleasedEvent;
import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.UserRepository;
import com.andrija.homesiloserver.user.UserRole;
import com.andrija.homesiloserver.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Account management for admins. An admin can't disable, demote, delete or reset
 * the password of their own account here, which also guarantees the server always
 * keeps at least one active admin (the one making the change).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    /** Columns the user list can be sorted by. Storage used is computed, so it isn't one of them. */
    static final Set<String> SORTABLE = Set.of(
            "username", "email", "role", "enabled", "createdAt", "lastLoginAt", "storageQuotaBytes");

    private final UserRepository userRepository;
    private final UserService userService;
    private final FileMetadataRepository fileRepository;
    private final FolderRepository folderRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final HomesiloProperties properties;
    private final ActivityRecorder activity;

    public AdminOverviewResponse overview() {
        return new AdminOverviewResponse(
                userRepository.count(),
                userRepository.countByEnabledTrue(),
                userRepository.countByRole(UserRole.ADMIN),
                fileRepository.sumAllSizes(),
                userRepository.sumStorageQuotaBytes(),
                fileRepository.countByTrashedFalse(),
                properties.storage().defaultQuota().toBytes(),
                properties.auth().registrationEnabled(),
                fileRepository.topUsage(PageRequest.of(0, 5)).stream()
                        .map(u -> new AdminOverviewResponse.StorageByUser(u.getOwnerId(), u.getUsername(), u.getBytes()))
                        .toList());
    }

    public PageResponse<AdminUserResponse> list(String query, UserRole role, AccountStatus status, Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new BadRequestException("Users can't be sorted by '" + order.getProperty() + "'");
            }
        }
        Page<User> page = userRepository.findAll(UserSpecifications.matching(query, role, status), pageable);
        Map<UUID, OwnerUsage> usage = page.isEmpty() ? Map.of()
                : fileRepository.usageByOwners(page.map(User::getId).getContent()).stream()
                        .collect(Collectors.toMap(OwnerUsage::getOwnerId, Function.identity()));
        return PageResponse.from(page.map(user -> response(user, usage.get(user.getId()))));
    }

    public AdminUserResponse get(UUID userId) {
        return withUsage(userService.getEntity(userId));
    }

    @Transactional
    public AdminUserResponse create(UUID actingAdminId, CreateUserRequest request) {
        long quota = request.storageQuotaBytes() != null
                ? request.storageQuotaBytes()
                : properties.storage().defaultQuota().toBytes();
        User user = userService.createAccount(
                request.username(), request.email(), request.password(), request.role(), quota, false);
        log.info("Admin {} created user '{}' ({}) with role {}", actingAdminId, user.getUsername(), user.getId(), user.getRole());
        activity.record(ActivityAction.USER_CREATED, TargetType.USER, user.getId(), user.getUsername(),
                null, user.getRole().name());
        return AdminUserResponse.from(user, 0, 0);
    }

    @Transactional
    public AdminUserResponse update(UUID actingAdminId, UUID userId, UpdateUserRequest request) {
        User user = userService.getEntity(userId);
        if (userId.equals(actingAdminId)) {
            if (Boolean.FALSE.equals(request.enabled())) {
                throw new ConflictException("You can't disable your own account");
            }
            if (request.role() != null && request.role() != UserRole.ADMIN) {
                throw new ConflictException("You can't remove your own admin role");
            }
        }

        // One log entry per change, so the history reads "made Admin", "limit 100 GB → 150 GB".
        if (request.enabled() != null && request.enabled() != user.isEnabled()) {
            user.setEnabled(request.enabled());
            activity.record(request.enabled() ? ActivityAction.USER_ENABLED : ActivityAction.USER_DISABLED,
                    TargetType.USER, userId, user.getUsername());
        }
        if (request.role() != null && request.role() != user.getRole()) {
            String previous = user.getRole().name();
            user.setRole(request.role());
            activity.record(ActivityAction.USER_ROLE_CHANGED, TargetType.USER, userId, user.getUsername(),
                    previous, request.role().name());
        }
        if (request.storageQuotaBytes() != null && request.storageQuotaBytes() != user.getStorageQuotaBytes()) {
            String previous = String.valueOf(user.getStorageQuotaBytes());
            user.setStorageQuotaBytes(request.storageQuotaBytes());
            activity.record(ActivityAction.USER_STORAGE_LIMIT_CHANGED, TargetType.USER, userId, user.getUsername(),
                    previous, String.valueOf(request.storageQuotaBytes()));
        }

        log.info("Admin {} updated user {}: {}", actingAdminId, userId, request);
        return withUsage(user);
    }

    /** Sets a new password and signs the person out everywhere (their old sessions stop working). */
    @Transactional
    public void resetPassword(UUID actingAdminId, UUID userId, ResetPasswordRequest request) {
        if (userId.equals(actingAdminId)) {
            throw new ConflictException("Change your own password in Settings");
        }
        User user = userService.getEntity(userId);
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        log.info("Admin {} reset the password of user {}", actingAdminId, userId);
        activity.record(ActivityAction.USER_PASSWORD_RESET, TargetType.USER, userId, user.getUsername());
    }

    /** Deletes the account with all of its files and folders. Stored blobs are released after commit. */
    @Transactional
    public void delete(UUID actingAdminId, UUID userId) {
        if (userId.equals(actingAdminId)) {
            throw new ConflictException("You can't delete your own account");
        }
        User user = userService.getEntity(userId);
        String username = user.getUsername();

        List<String> blobs = fileRepository.findBlobsByOwner(userId);
        int files = fileRepository.deleteAllByOwner(userId);
        folderRepository.detachAllByOwner(userId);
        int folders = folderRepository.deleteAllByOwner(userId);
        userRepository.deleteById(userId);
        if (!blobs.isEmpty()) {
            events.publishEvent(new BlobsReleasedEvent(userId, blobs));
        }
        log.info("Admin {} deleted user '{}' ({}) with {} file(s) and {} folder(s)",
                actingAdminId, username, userId, files, folders);
        activity.record(ActivityAction.USER_DELETED, TargetType.USER, userId, username, null, String.valueOf(files));
    }

    private AdminUserResponse withUsage(User user) {
        List<OwnerUsage> usage = fileRepository.usageByOwners(List.of(user.getId()));
        return response(user, usage.isEmpty() ? null : usage.getFirst());
    }

    private static AdminUserResponse response(User user, OwnerUsage usage) {
        return usage == null
                ? AdminUserResponse.from(user, 0, 0)
                : AdminUserResponse.from(user, usage.getBytes(), usage.getFiles());
    }
}
